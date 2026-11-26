package pl.databucket.e2e.support;

import pl.databucket.e2e.client.Management;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;

/**
 * A project of its own for one test, with four users (ADMIN, MEMBER, ROBOT, AGENT) assigned to it. Tests don't see each
 * other's data, so they can run in parallel. Removed after the test (cleared and deleted; the users are disabled,
 * as users can't be deleted). Given to tests and their {@code @BeforeEach} methods by {@link ProjectExtension}.
 */
public final class TestProject implements AutoCloseable {

    /** The features of views have the ids 1..17 (17 = Generate, Databucket 3.20.0+). */
    public static final int ALL_FEATURES = 17;
    public static final int FEATURE_GENERATE = 17;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Api superApi;
    private final long id;
    private final String name;
    private final Map<UserKey, TestUser> users;
    private final Map<UserKey, String> tokens = new EnumMap<>(UserKey.class);

    private TestProject(Api superApi, long id, String name, Map<UserKey, TestUser> users) {
        this.superApi = superApi;
        this.id = id;
        this.name = name;
        this.users = users;
    }

    public static TestProject create() {
        ApiResponse signIn = new Api(Env.SUPER_USER, null, true).post("/api/public/signin",
                call().body(obj("username", Env.SUPER_USER, "password", Env.SUPER_PASSWORD)).expect(200));
        Api superApi = new Api(Env.SUPER_USER, signIn.path("token"), true);

        // unique and within the limits: project name up to 30 characters, username up to 50
        String suffix = Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RANDOM.nextInt(36 * 36 * 36 * 36), 36);
        String name = "e2e-" + suffix;
        ApiResponse project = superApi.post("/api/manage/projects",
                call().body(obj("name", name, "description", "Created by the e2e tests", "enabled", true)).expect(201));
        long projectId = project.<Number>path("id").longValue();

        Map<UserKey, TestUser> users = new EnumMap<>(UserKey.class);
        for (UserKey key : UserKey.values()) {
            TestUser user = new TestUser(name + "-" + key.key(), Env.TEST_PASSWORD, key.role);
            superApi.post("/api/manage/users", call().body(obj(
                    "username", user.username(), "enabled", true, "rolesIds", arr(user.role().id), "projectsIds", arr(projectId)
            )).expect(201));
            // a new user has no usable password: set a temporary one, then change it, so it isn't a forced change any more
            String temporary = "tmp-" + suffix;
            superApi.post("/api/manage/users/password/reset",
                    call().body(obj("username", user.username(), "password", temporary)).expect(200));
            ApiResponse first = new Api(user.username(), null, true).post("/api/public/signin",
                    call().body(obj("username", user.username(), "password", temporary)).expect(200));
            new Api(user.username(), first.path("token"), true).post("/api/users/password/change",
                    call().body(obj("username", user.username(), "password", temporary, "newPassword", user.password())).expect(200));
            users.put(key, user);
        }
        TestProject created = new TestProject(superApi, projectId, name, users);
        // the icons of Databucket (Settings > Icons), the outlined one for the buckets of createBucket
        for (Map<String, Object> icon : List.of(
                obj("name", DatabucketIcons.OUTLINED_NAME, "structure", DatabucketIcons.OUTLINED),
                obj("name", DatabucketIcons.FILLED_NAME, "structure", DatabucketIcons.FILLED)))
            created.quietly(UserKey.ADMIN).post("/api/svg", call().body(icon).expect(201));
        return created;
    }

    /** The management of all projects and users, as the SUPER user that made this project. */
    public Management management() {
        return new Management(superApi.loud());
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TestUser user(UserKey key) {
        return users.get(key);
    }

    /** The user signed in to this project; his calls are reported. */
    public Api as(UserKey key) {
        return signedIn(key).loud();
    }

    /** The user signed in to this project, not reported: for preparing data. */
    public Api quietly(UserKey key) {
        return signedIn(key);
    }

    /** A JWT of the user for this project. */
    public String token(UserKey key) {
        return signedIn(key).token();
    }

    private synchronized Api signedIn(UserKey key) {
        TestUser user = users.get(key);
        String token = tokens.get(key);
        if (token == null) {
            ApiResponse response = new Api(user.username(), null, true).post("/api/public/signin",
                    call().body(obj("username", user.username(), "password", user.password(), "projectId", id)).expect(200));
            token = response.path("token");
            tokens.put(key, token);
        }
        return new Api(key.key(), token, true);
    }

    /** Creates a bucket every user of the project can use (access from AGENT up), with history and the outlined icon of Databucket. */
    public long createBucket(String name) {
        return createBucket(name, true, false);
    }

    /** Creates a bucket every user of the project can use (access from AGENT up). */
    public long createBucket(String name, boolean history, boolean protectedData) {
        ApiResponse response = quietly(UserKey.ADMIN).post("/api/buckets", call().body(obj(
                "name", name,
                "icon", DatabucketIcons.outlinedJson(),
                "history", history,
                "protectedData", protectedData,
                "roleId", Role.AGENT.id
        )).expect(201));
        return response.<Number>path("id").longValue();
    }

    /**
     * Creates a view of the bucket (every user from ROBOT up, all features) with columns for the given properties,
     * in the given order, e.g. {@code obj("customer", "Customer")} shows {@code $.customer} as "Customer". Without a
     * view the GUI shows no data.
     */
    public long createView(long bucketId, String name, Map<String, ?> properties) {
        return createView(bucketId, name, properties, IntStream.rangeClosed(1, ALL_FEATURES).boxed().toList());
    }

    /** As {@link #createView(long, String, Map)}, with only the given features (ids as in the settings of views). */
    public long createView(long bucketId, String name, Map<String, ?> properties, List<Integer> features) {
        Api admin = quietly(UserKey.ADMIN);
        List<Object> props = new ArrayList<>();
        List<String> uuids = new ArrayList<>(List.of("uuid_data_id", "uuid_reserved", "uuid_owner"));
        properties.forEach((path, title) -> {
            props.add(obj("path", "$." + path, "type", "string", "uuid", "prop-" + path, "title", title));
            uuids.add("prop-" + path);
        });
        uuids.add("uuid_modified_at");
        List<Object> columns = new ArrayList<>();
        for (String uuid : uuids) {
            columns.add(obj("uuid", uuid, "enabled", true, "sorting", true, "filtering", true,
                    "editable", uuid.startsWith("prop-") || uuid.equals("uuid_reserved") ? "always" : "never"));
        }
        ApiResponse created = admin.post("/api/columns", call().body(obj(
                "name", name + " columns",
                "configuration", obj("properties", props, "columns", columns)
        )).expect(201));
        ApiResponse view = admin.post("/api/views", call().body(obj(
                "name", name,
                "columnsId", created.<Number>path("id").longValue(),
                "bucketsIds", arr(bucketId),
                "roleId", Role.ROBOT.id,
                "featuresIds", features,
                "sort", "desc(id)"
        )).expect(201));
        return view.<Number>path("id").longValue();
    }

    @Override
    public void close() {
        superApi.post("/api/manage/projects/" + id + "/clear", call().expect(204));
        superApi.delete("/api/manage/projects/" + id, call().expect(204));
        for (TestUser user : users.values()) {
            superApi.put("/api/manage/users", call().body(obj(
                    "username", user.username(), "description", "Created by the e2e tests", "enabled", false,
                    "rolesIds", arr(user.role().id), "projectsIds", arr()
            )));
        }
    }
}
