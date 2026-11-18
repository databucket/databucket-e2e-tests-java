package pl.databucket.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An HTML view of one HTTP call (request and response) for the Allure report: an attachment of type text/html, which
 * the report shows in a frame.
 * <ul>
 * <li>the first line: method, URL, a Copy button</li>
 * <li>the request (headers collapsed, body open; highlighted JSON), then the response with its status and time</li>
 * <li>a Copy button: request and response, with or without headers, as plain text</li>
 * </ul>
 * The same view as in the TypeScript tests (databucket-e2e-tests, support/http-view.ts).
 */
public final class HttpView {

    /**
     * One call; bodies are JSON (masked) or a text node when the body isn't JSON, null when empty.
     */
    public record Exchange(String method, String url, Map<String, String> requestHeaders, JsonNode requestBody,
                           int status, String statusText, long took, Map<String, String> responseHeaders,
                           JsonNode responseBody) {
    }

    /** Bodies longer than this start collapsed. */
    private static final int LONG_BODY_LINES = 300;

    private static final String STYLE = """
              /* the theme of the report: color-scheme dark on the report's page is seen by the frame as prefers-color-scheme */
              :root { color-scheme: light dark; }
              body { margin: 0; padding: 10px 12px; font: 13px/1.45 -apple-system, "Segoe UI", Roboto, Arial, sans-serif; color: #1f2328; background: transparent; }
              .mono { font-family: Consolas, "Cascadia Mono", Menlo, monospace; }
              .summary { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
              .url { word-break: break-all; }
              .badge { padding: 2px 8px; border-radius: 4px; font-weight: 600; font-size: 12px; color: #fff; white-space: nowrap; }
              /* method colors as in Bruno (PATCH, the same as PUT there, gets its own) */
              .GET { background: #059669; } .POST { background: #8e44ad; } .PUT { background: #ca7811; } .DELETE { background: #b91c1c; }
              .PATCH { background: #0e7490; } .HEAD, .OPTIONS { background: #6e7781; }
              .s2 { background: #1a7f37; } .s3 { background: #6e7781; } .s4 { background: #bc4c00; } .s5 { background: #cf222e; }
              .muted { color: #6e7781; }
              .toolbar { display: flex; align-items: center; gap: 6px; margin-left: auto; }
              .toolbar label { margin-right: 4px; color: #57606a; font-size: 12px; cursor: pointer; user-select: none; white-space: nowrap; }
              .request { margin-top: 6px; }
              button { font: inherit; font-size: 12px; padding: 3px 10px; border: 1px solid #d0d7de; border-radius: 6px; background: #f6f8fa; color: #1f2328; cursor: pointer; }
              button:hover { background: #eaeef2; }
              button.done { border-color: #1a7f37; color: #1a7f37; }
              section { margin-top: 12px; border-top: 1px solid #d8dee4; padding-top: 6px; }
              h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 4px; font-size: 12px; font-weight: 600; color: #57606a; }
              h3 .title { text-transform: uppercase; letter-spacing: .05em; }
              h3 .muted { font-weight: 400; }
              details { margin: 4px 0; }
              summary { cursor: pointer; color: #57606a; font-size: 12px; user-select: none; }
              table { border-collapse: collapse; font-size: 12px; margin: 4px 0 0 14px; }
              td { padding: 1px 16px 1px 0; vertical-align: top; word-break: break-all; }
              td:first-child { color: #6e7781; white-space: nowrap; }
              pre { margin: 4px 0 0; padding: 8px 10px; background: #f6f8fa; border: 1px solid #d0d7de; border-radius: 6px;
                    font-size: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
              .error pre { border-color: #ff8182; background: #fff8f8; }
              .k { color: #0550ae; } .str { color: #116329; } .num { color: #953800; } .lit { color: #8250df; font-weight: 600; }
              @media (prefers-color-scheme: dark) {
                body { color: #e6edf3; }
                .muted, summary, h3, td:first-child, .toolbar label { color: #8d96a0; }
                section { border-top-color: #30363d; }
                pre { background: #161b22; border-color: #30363d; }
                .error pre { border-color: #f85149; background: #2d1214; }
                button { background: #21262d; border-color: #30363d; color: #e6edf3; }
                button:hover { background: #30363d; }
                button.done { border-color: #3fb950; color: #3fb950; }
                .k { color: #79c0ff; } .str { color: #7ee787; } .num { color: #ffa657; } .lit { color: #d2a8ff; }
              }
            """;

    /** Builds the copied text on click; the data of the call is embedded as JSON. */
    private static final String SCRIPT = """
              const x = JSON.parse(document.getElementById('exchange').textContent);
              const body = b => b === undefined || b === null || b === '' ? '' : (typeof b === 'string' ? b : JSON.stringify(b, null, 2));
              const headers = h => Object.entries(h).map(([n, v]) => n + ': ' + v).join('\\n');
              const content = (h, b, withHeaders) => {
                const parts = [];
                if (withHeaders && Object.keys(h).length) parts.push(headers(h));
                if (body(b)) parts.push(body(b));
                return parts.join('\\n') || '(empty)';
              };
              // plain text, no formatting (wrap it in a code block yourself where you paste it):
              //   POST http://...
              //   headers if wanted, body
              //
              //   Response: 200 OK (21 ms)
              //   headers if wanted, body
              const message = h => [
                x.method + ' ' + x.url,
                content(x.requestHeaders, x.requestBody, h),
                '',
                'Response: ' + x.status + ' ' + x.statusText + ' (' + x.took + ' ms)',
                content(x.responseHeaders, x.responseBody, h),
              ].join('\\n');
              // the frame of the report may have no clipboard permission: a copy event filled by hand, then the API
              const copy = text => {
                let filled = false;
                const onCopy = e => {
                  e.clipboardData.setData('text/plain', text);
                  e.preventDefault();
                  filled = true;
                };
                document.addEventListener('copy', onCopy);
                try { document.execCommand('copy'); } catch (e) { /* not supported */ }
                document.removeEventListener('copy', onCopy);
                if (filled) return Promise.resolve();
                return navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject(new Error('no clipboard'));
              };
              // The height of the frame, where the report injects its resize script (the allure2 plugin of Allure 3):
              // it measures the larger of the content and the frame, so the frame never shrinks. Send the height of the
              // content through the same message (its type and token are in that script). Without that script: nothing.
              const allureToken = (() => {
                for (const script of document.scripts) {
                  const match = script.textContent.match(/const token = "([^"]+)"/);
                  if (match) return match[1];
                }
                return null;
              })();
              const fit = () => {
                if (!allureToken) return;
                const height = Math.ceil(document.body.getBoundingClientRect().height);
                parent.postMessage({ type: 'allure:html-preview:resize', token: allureToken, height }, '*');
              };
              // after the report's own message (sent on the next animation frame)
              const fitLater = () => setTimeout(fit, 50);
              window.addEventListener('load', fitLater);
              document.addEventListener('toggle', fitLater, true);
              fitLater();
              document.querySelectorAll('button.copy').forEach(button => {
                const label = button.textContent;
                button.addEventListener('click', () => {
                  copy(message(document.getElementById('with-headers').checked)).then(
                    () => { button.textContent = 'Copied ✓'; button.classList.add('done'); },
                    () => { button.textContent = 'Copy failed'; },
                  ).finally(() => setTimeout(() => { button.textContent = label; button.classList.remove('done'); }, 1500));
                });
              });
            """;

    /** Tokens of pretty-printed (and HTML-escaped) JSON: strings (keys when a colon follows), literals, numbers. */
    private static final Pattern TOKENS = Pattern.compile(
            "(&quot;(?:\\\\.|(?!&quot;).)*?&quot;)(\\s*:)?|\\b(true|false|null)\\b|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)");

    private HttpView() {
    }

    public static String render(Exchange x) {
        boolean failed = x.status() >= 400;
        String data = Json.write(x).replace("<", "\\u003c");
        return "<!doctype html><html><head><meta charset=\"utf-8\"><style>" + STYLE + "</style></head><body>"
                + "<div class=\"summary\"><span class=\"badge " + esc(x.method()) + "\">" + esc(x.method()) + "</span>"
                + "<span class=\"url mono\">" + esc(x.url()) + "</span>"
                + "<div class=\"toolbar\"><label><input type=\"checkbox\" id=\"with-headers\"> with headers</label>"
                + "<button class=\"copy\">Copy</button></div></div>"
                + "<div class=\"request\">" + headersBlock(x.requestHeaders()) + bodyBlock(x.requestBody()) + "</div>"
                + "<section class=\"" + (failed ? "error" : "") + "\"><h3><span class=\"title\">Response</span>"
                + "<span class=\"badge s" + String.valueOf(x.status()).charAt(0) + "\">" + x.status() + " " + esc(x.statusText()) + "</span>"
                + "<span class=\"muted\">" + x.took() + " ms</span></h3>"
                + headersBlock(x.responseHeaders()) + bodyBlock(x.responseBody()) + "</section>"
                + "<script type=\"application/json\" id=\"exchange\">" + data + "</script><script>" + SCRIPT + "</script>"
                + "</body></html>";
    }

    private static String headersBlock(Map<String, String> headers) {
        if (headers.isEmpty()) return "<details><summary>Headers (none)</summary></details>";
        StringBuilder table = new StringBuilder();
        headers.forEach((name, value) -> table.append("<tr><td>").append(esc(name)).append("</td><td>").append(esc(value)).append("</td></tr>"));
        return "<details><summary>Headers (" + headers.size() + ")</summary><table class=\"mono\">" + table + "</table></details>";
    }

    private static String bodyBlock(JsonNode body) {
        if (body == null || body.isNull() || (body.isTextual() && body.asText().isEmpty()))
            return "<details><summary>Body (empty)</summary></details>";
        String text = body.isTextual() ? body.asText() : Json.pretty(body);
        long lines = text.lines().count();
        boolean isLong = lines > LONG_BODY_LINES;
        String content = body.isTextual() ? esc(text) : highlight(text);
        return "<details" + (isLong ? "" : " open") + "><summary>Body" + (isLong ? " (" + lines + " lines)" : "")
                + "</summary><pre class=\"mono\">" + content + "</pre></details>";
    }

    /** Colors the tokens of pretty-printed JSON: keys, strings, numbers, true / false / null. */
    private static String highlight(String json) {
        Matcher matcher = TOKENS.matcher(esc(json));
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1) != null) {
                replacement = matcher.group(2) != null
                        ? "<span class=\"k\">" + matcher.group(1) + "</span>" + matcher.group(2)
                        : "<span class=\"str\">" + matcher.group(1) + "</span>";
            } else if (matcher.group(3) != null) {
                replacement = "<span class=\"lit\">" + matcher.group(3) + "</span>";
            } else {
                replacement = "<span class=\"num\">" + matcher.group(4) + "</span>";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
