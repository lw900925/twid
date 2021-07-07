package io.lw900925.twid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "twi")
@Component
public class TwidProperties {

    private Twitter twitter;
    private String list;
    private String cache;
    private String location;

    public static class Twitter {

        private Api api;
        private Proxy proxy;
        private Integer size = 200;
        private Boolean increment = true;

        public static class Api {
            private String baseUrl;
            private String accessToken;

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getAccessToken() {
                return accessToken;
            }

            public void setAccessToken(String accessToken) {
                this.accessToken = accessToken;
            }
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

        public Api getApi() {
            return api;
        }

        public void setApi(Api api) {
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

        public Boolean getIncrement() {
            return increment;
        }

        public void setIncrement(Boolean increment) {
            this.increment = increment;
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
