# spring-boot-url-proxy
Proxy any HTTP call through this URL proxy.

## How to Run?
- Download and build with maven: `mvn package`
- run with java: `java -jar target/spring-boot-url-proxy-1.0-SNAPSHOT.jar`

## How to Use?

This proxy has only 1 (one) endpoint at: `/proxy`
The endpoint takes only 1 (one) URL Parameter: `url`, which defines the URL being proxied. The target URL.
The endpoint responds to any HTTP Method and honors any Headers. Both Method and Headers are proxied to the target URL.

## Examples

### GET

```
curl -i -X GET \
   -H "Host:myhost" \
 'http://localhost:8080/proxy?url=www.google.com'
```
Will result in this call from the proxy host:

```
curl -i -X GET \
   -H "Host:myhost" \
 'http://www.google.com'
```

### POST

```
curl -i -X POST \
   -H "Content-Type:application/json" \
   -d \
'{
  "test":"test"
}' \
 'http://localhost:8080/proxy?url=http%3A%2F%2Flocalhost%3A9999%2Fendpoint%2Ftest'
```

Will result in this call from the proxy host:

```
curl -i -X POST \
   -H "Content-Type:application/json" \
   -d \
'{
  "test":"test"
}' \
 'http://localhost:8080/endpoint/test'
```

### Other HTTP Methods

It works for any other HTTP Method, standard or custom. Headers and Method are blindly passed to the proxied call to the target URL.
