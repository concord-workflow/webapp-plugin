package ca.ibodrov.concord.webapp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * Serves static resources SPA-style. To support client-side routing, known
 * files served as usual, other URLs always return the content of index.html.
 * <p/>
 * The class expects a CSV file with checksums of the UI resources to be present
 * in the classpath. The file can be generated using checksum-maven-plugin.
 */
public class SpaServlet extends HttpServlet {

    private final String resourceRoot;
    private final String indexHtmlRelativePath;
    private final Map<String, StaticResource> resources;

    public SpaServlet(String checksumsFileResourcePath,
                      String resourceRoot,
                      String indexHtmlRelativePath) {

        this.resourceRoot = requireNonNull(resourceRoot);
        this.indexHtmlRelativePath = requireNonNull(indexHtmlRelativePath);
        resources = loadResources(requireNonNull(checksumsFileResourcePath));
        // a quick sanity check
        if (resources.get(indexHtmlRelativePath) == null) {
            throw new RuntimeException("Missing the root UI resource: %s. Classpath issues?"
                    .formatted(resourceRoot + indexHtmlRelativePath));
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        var path = req.getPathInfo();

        if (path == null) {
            path = "";
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.isEmpty() || path.equals("/")) {
            path = "index.html";
        }

        try {
            var resource = Optional.ofNullable(resources.get(path))
                    .orElseGet(() -> resources.get(indexHtmlRelativePath));

            var filePath = resourceRoot + resource.path();
            try (var in = SpaServlet.class.getClassLoader().getResourceAsStream(filePath)) {
                if (in == null) {
                    throw new RuntimeException("Resource not found: " + filePath);
                }

                resp.setHeader("Content-Type", resource.contentType());
                resp.setHeader("ETag", resource.eTag());

                var ifNoneMatch = req.getHeader("If-None-Match");
                if (resource.eTag().equals(ifNoneMatch)) {
                    resp.setStatus(304);
                } else {
                    resp.setStatus(200);
                    ByteStreams.copy(in, resp.getOutputStream());
                }
            }
        } catch (IOException e) {
            throw new WebApplicationException(INTERNAL_SERVER_ERROR);
        }
    }

    private static Optional<String> getContentType(String fileName) {
        var extIdx = fileName.lastIndexOf('.');
        if (extIdx < 2 || extIdx >= fileName.length() - 1) {
            return Optional.empty();
        }
        var ext = fileName.substring(extIdx + 1);
        return Optional.ofNullable(switch (ext) {
            case "css" -> "text/css";
            case "html" -> "text/html";
            case "js" -> "text/javascript";
            case "svg" -> "image/svg+xml";
            case "ttf" -> "font/ttf";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> null;
        });
    }

    private static Map<String, StaticResource> loadResources(String file) {
        var resources = ImmutableMap.<String, StaticResource>builder();

        var cl = SpaServlet.class.getClassLoader();
        try (var in = cl.getResourceAsStream(file)) {
            if (in == null) {
                throw new RuntimeException(file + " file not found. Classpath or build issues?");
            }

            try (var reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.startsWith("#")) {
                        continue;
                    }

                    var items = line.split(",");
                    if (items.length != 2) {
                        throw new RuntimeException(file + " file, invalid line: " + line);
                    }

                    var path = items[0];
                    var eTag = items[1];
                    var contentType = getContentType(path)
                            .orElseThrow(() -> new RuntimeException("Can't determine Content-Type for " + path));

                    resources.put(path, new StaticResource(path, contentType, eTag));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return resources.build();
    }

    private record StaticResource(String path, String contentType, String eTag) {
    }
}
