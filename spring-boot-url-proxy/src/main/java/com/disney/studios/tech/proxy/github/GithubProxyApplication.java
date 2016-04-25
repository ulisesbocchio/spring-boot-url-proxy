package com.disney.studios.tech.proxy.github;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HttpServletBean;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javaslang.collection.List;
import javaslang.collection.Stream;
import javaslang.control.Try;

/**
 * @author Ulises Bocchio
 */
@SpringBootApplication
@Slf4j
public class GithubProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(GithubProxyApplication.class, args);
    }

    @Bean
    ServletRegistrationBean proxyServletConfig() {
        ServletRegistrationBean servletRegistrationBean = new ServletRegistrationBean(proxyServlet(), true, "/proxy");
        return servletRegistrationBean;
    }

    @Bean
    HttpServletBean proxyServlet() {
        return new HttpServletBean() {

            @Autowired
            ObjectMapper json;

            @Autowired
            HttpClient httpClient;

            @Override
            public void service(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
                String url = req.getParameter("url");
                if (url == null) {
                    error(req, res, HttpStatus.BAD_REQUEST, "No URL Parameter found");
                    return;
                }
                if (!isURL(url)) {
                    error(req, res, HttpStatus.BAD_REQUEST, "URL '" + url + "' is malformed");
                    return;
                }

                try {
                    RequestBuilder requestBuilder = RequestBuilder.create(req.getMethod());
                    Collections.list(req.getHeaderNames())
                        .stream()
                        .filter(name -> !name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
                        .forEach(header -> requestBuilder.setHeader(header, List.ofAll(Collections.list(req.getHeaders(header))).mkString(",")));
                    requestBuilder.setHeader("X-Forwarded-For", req.getRemoteAddr());
                    if(req.getCharacterEncoding() != null) {
                        requestBuilder.setCharset(Charset.forName(req.getCharacterEncoding()));
                    }
                    requestBuilder.setUri(url);
                    requestBuilder.setEntity(new InputStreamEntity(req.getInputStream()));
                    HttpUriRequest proxyRequest = requestBuilder.build();
                    HttpResponse proxyResponse = httpClient.execute(proxyRequest);
                    req.getInputStream().close();
                    res.setStatus(proxyResponse.getStatusLine().getStatusCode());
                    res.setHeader("Proxied-By", "Spring Proxy");
                    if(proxyResponse.getEntity() != null) {
                        if(proxyResponse.getEntity().getContentEncoding() != null) {
                            res.setCharacterEncoding(proxyResponse.getEntity().getContentEncoding().getValue());
                        }
                        Stream.of(proxyResponse.getAllHeaders()).forEach(h -> res.setHeader(h.getName(), h.getValue()));
                        try(OutputStream out = res.getOutputStream()) {
                            proxyResponse.getEntity().writeTo(out);
                            out.flush();
                        }
                    }
                    res.flushBuffer();

                } catch (Throwable t) {
                    log.error("Error caught:", t);
                    error(req, res, HttpStatus.INTERNAL_SERVER_ERROR, exceptionMessage(t));
                }
            }

            private boolean isURL(String url) {
                return Try.of(() -> new URL(url)).isSuccess();
            }

            @SneakyThrows
            private void error(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String message) {
                Error error = new Error(message, status.value(), status.getReasonPhrase());
                String body = json.writeValueAsString(error);
                log.warn("Sending Error to client from req {}. Error: {}", req.getRequestURI(), body);
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.setCharacterEncoding("UTF-8");
                res.setStatus(status.value());
                res.setContentLength(body.getBytes().length);
                try (OutputStreamWriter resWriter = new OutputStreamWriter(res.getOutputStream(), "UTF-8")) {
                    resWriter.write(body);
                    resWriter.flush();
                    res.flushBuffer();
                } catch (Throwable t) {
                    log.error("Error caught generating user error:", exceptionMessage(t));
                    res.sendError(500, t.getMessage());
                }
            }

            private String exceptionMessage(Throwable t) {
                return t.getCause() != null ? t.getCause().getMessage() : (t.getMessage() != null ? t.getMessage() : t.getClass().toString());
            }
        };

    }

    @RequestMapping("/test")
    @RestController
    public static class TestEndpoints {

        @RequestMapping(method = RequestMethod.GET)
        public ResponseEntity<String> get() {
            return ResponseEntity.ok().header("SILLY", "Silly").body("Hello Buddy");
        }

        @RequestMapping(method = RequestMethod.GET, params = {"noContent"})
        public ResponseEntity<Void> getNoContent() {
            return ResponseEntity.ok().header("SILLY", "Silly").build();
        }

        @RequestMapping(method = RequestMethod.POST)
        public ResponseEntity<String> post(@RequestBody String body) {
            return ResponseEntity.ok().header("SILLY", "Silly").body(body);
        }

        @RequestMapping(method = RequestMethod.POST, params = {"noContent"})
        public ResponseEntity<Void> postNoContent() {
            return getNoContent();
        }
    }

    @Bean
    public HttpClientConnectionManager connectionManager() {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setValidateAfterInactivity(10000);
        connManager.setMaxTotal(1000);
        connManager.setDefaultMaxPerRoute(100);
        return connManager;
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient httpClient() {
        return HttpClients.custom()
            .setConnectionManager(connectionManager())
            .setDefaultRequestConfig(requestConfig())
            .build();
    }

    @Bean
    public RequestConfig requestConfig() {
        return RequestConfig.custom()
            .setConnectionRequestTimeout(5000)
            .setConnectTimeout(10000)
            .setSocketTimeout(10000)
            .setRedirectsEnabled(false)
            .build();
    }


    @Data
    @AllArgsConstructor
    private static class Error {

        private String message;
        private int status;
        private String reason;
    }

}
