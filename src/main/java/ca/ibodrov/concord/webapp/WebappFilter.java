package ca.ibodrov.concord.webapp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

import javax.annotation.Priority;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * @implNote The filter should be applied after all other filters.
 */
@WebFilter("/*")
@Priority(Integer.MAX_VALUE - 1000)
public class WebappFilter extends HttpFilter {

    private final WebappCollection webapps;

    public WebappFilter() {
        this.webapps = loadWebapps();
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        var uri = req.getRequestURI();
        var webapp = webapps.stream().filter(w -> uri.startsWith(w.path())).findFirst();
        if (webapp.isPresent()) {
            doGet(webapp.get(), req, resp);
        } else {
            chain.doFilter(req, resp);
        }
    }

    private void doGet(Webapp webapp, HttpServletRequest req, HttpServletResponse resp) {
        var path = req.getRequestURI();

        if (!path.startsWith(webapp.path())) {
            throw new RuntimeException("Unexpected path: " + path);
        }

        path = path.length() > webapp.path().length() ? path.substring(webapp.path().length()) : "";

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.isEmpty() || path.equals("/")) {
            path = "index.html";
        }

        try {
            var resource = Optional.ofNullable(webapp.resources().get(path))
                    .orElseGet(() -> webapp.resources().get(webapp.indexHtmlRelativePath()));

            var filePath = webapp.resourceRoot() + resource.path();
            try (var in = WebappFilter.class.getClassLoader().getResourceAsStream(filePath)) {
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

    private static WebappCollection loadWebapps() {
        var result = new ArrayList<Webapp>();
        var classLoader = WebappPluginModule.class.getClassLoader();
        try {
            classLoader.getResources("META-INF/concord/webapp.properties")
                    .asIterator()
                    .forEachRemaining(source -> {
                        var webapp = Webapp.parse(source);
                        result.add(webapp);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new WebappCollection(result);
    }

    private record Webapp(String path,
            Map<String, StaticResource> resources,
            String resourceRoot,
            String indexHtmlRelativePath) {

        public static Webapp parse(URL source) {
            var props = new Properties();
            try (var in = source.openStream()) {
                props.load(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            var path = assertString(source, props, "path");
            var resources = loadResources(assertString(source, props, "checksumsFileResourcePath"));
            var resourceRoot = assertString(source, props, "resourceRoot");
            var indexHtmlRelativePath = assertString(source, props, "indexHtmlRelativePath");
            return new Webapp(path, resources, resourceRoot, indexHtmlRelativePath);
        }
    }

    private static class WebappCollection {

        // must be sorted, longest prefixes first
        private final List<Webapp> webapps;

        public WebappCollection(Collection<Webapp> webapps) {
            this.webapps = webapps.stream()
                    .sorted(comparing(Webapp::path).reversed())
                    .toList();
        }

        public Stream<Webapp> stream() {
            return webapps.stream();
        }
    }

    private static Map<String, StaticResource> loadResources(String file) {
        var resources = ImmutableMap.<String, StaticResource>builder();

        var cl = WebappFilter.class.getClassLoader();
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

    private static String assertString(URL source, Properties props, String key) {
        var value = props.getProperty(key);
        if (value == null) {
            throw new RuntimeException("Missing required property: %s (in %s)".formatted(key, source));
        }
        return value;
    }

    private static Optional<String> getContentType(String fileName) {
        var extIdx = fileName.lastIndexOf('.');
        if (extIdx < 2 || extIdx >= fileName.length() - 1) {
            return Optional.empty();
        }
        var ext = fileName.substring(extIdx + 1);
        return Optional.ofNullable(switch (ext) {
            case "css" -> "text/css";
            case "gif" -> "image/gif";
            case "html" -> "text/html";
            case "jpg", "jpeg" -> "image/jpeg";
            case "js" -> "text/javascript";
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            case "ttf" -> "font/ttf";
            case "webp" -> "image/webp";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> null;
        });
    }
}
