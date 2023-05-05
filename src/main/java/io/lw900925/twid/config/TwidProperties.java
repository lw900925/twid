package io.lw900925.twid.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "twi")
@Component
public class TwidProperties {

    private Twitter twitter;
    private String list;
    private String cache;
    private String location;

    public static class Twitter {

        /**
         * Maybe Bearer access token
         */
        private String accessToken;

        /**
         * Cookie
         */
        private String cookie;

        /**
         * APIs
         */
        private Map<API, ApiInfo> api;

        /**
         * HTTP proxy
         */
        private Proxy proxy;

        /**
         * Page size
         */
        private Integer size = 100;

        /**
         * Twitter Info
         */
        public static class ApiInfo {

            /**
             * URL
             */
            private String url;

            /**
             * Maybe query param
             */
            private Resource param;

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }

            public Resource getParam() {
                return param;
            }

            public void setParam(Resource param) {
                this.param = param;
            }
        }

        /**
         * Twitter APIs
         */
        public static enum API {
            /**
             * 媒体页
             */
            media,

            /**
             * 用户详情
             */
            user_info,

            ;
        }

        public static class Proxy {
            private String host;
            private Integer port;
            private String username;
            private String password;

            public String getHost() {
                return host;
            }

            public void setHost(String host) {
                this.host = host;
            }

            public Integer getPort() {
                return port;
            }

            public void setPort(Integer port) {
                this.port = port;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String username) {
                this.username = username;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String password) {
                this.password = password;
            }
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getCookie() {
            return cookie;
        }

        public void setCookie(String cookie) {
            this.cookie = cookie;
        }

        public Map<API, ApiInfo> getApi() {
            return api;
        }

        public void setApi(Map<API, ApiInfo> api) {
            this.api = api;
        }

        public Proxy getProxy() {
            return proxy;
        }

        public void setProxy(Proxy proxy) {
            this.proxy = proxy;
        }

        public Integer getSize() {
            return size;
        }

        public void setSize(Integer size) {
            this.size = size;
        }
    }

    public Twitter getTwitter() {
        return twitter;
    }

    public void setTwitter(Twitter twitter) {
        this.twitter = twitter;
    }

    public String getList() {
        return list;
    }

    public void setList(String list) {
        this.list = list;
    }

    public String getCache() {
        return cache;
    }

    public void setCache(String cache) {
        this.cache = cache;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
