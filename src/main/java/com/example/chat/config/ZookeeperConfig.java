package com.example.chat.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal Zookeeper configuration via Apache Curator.
 *
 * Purpose (per README):
 *  Service discovery — each chat server registers its hostname under
 *  /chat-service/servers/{instanceId} so clients can be directed to
 *  an available chat server.
 */
@Configuration
public class ZookeeperConfig {

    @Value("${chat.server.zookeeper.connect-string}")
    private String connectString;

    @Value("${chat.server.zookeeper.namespace}")
    private String namespace;

    @Value("${chat.server.zookeeper.session-timeout-ms}")
    private int sessionTimeoutMs;

    @Value("${chat.server.zookeeper.connection-timeout-ms}")
    private int connectionTimeoutMs;

    @Bean(destroyMethod = "close")
    public CuratorFramework curatorFramework() {
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .namespace(namespace)
                .sessionTimeoutMs(sessionTimeoutMs)
                .connectionTimeoutMs(connectionTimeoutMs)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        return client;
    }
}
