package com.example.chat.service;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service discovery via Zookeeper (Apache Curator) — minimal configuration.
 *
 * Each chat server registers an EPHEMERAL znode under /servers/{instanceId}.
 * Ephemeral nodes vanish on session loss (server crash). Clients list /servers
 * to get DNS hostnames of live chat servers.
 */
@Service
public class ServiceDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryService.class);
    private static final String SERVERS_PATH = "/servers";

    private final CuratorFramework curator;
    private final String instanceId;
    private final String serverAddress;
    private CuratorCache cache;

    public ServiceDiscoveryService(
            CuratorFramework curator,
            @Value("${chat.server.instance-id}") String instanceId,
            @Value("${server.port:8080}") int serverPort) {
        this.curator = curator;
        this.instanceId = instanceId;
        this.serverAddress = "localhost:" + serverPort;
    }

    @PostConstruct
    public void register() {
        String nodePath = SERVERS_PATH + "/" + instanceId;
        try {
            if (curator.checkExists().forPath(SERVERS_PATH) == null)
                curator.create().creatingParentsIfNeeded().forPath(SERVERS_PATH);
            curator.create()
                    .orSetData()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(nodePath, serverAddress.getBytes(StandardCharsets.UTF_8));
            log.info("Registered chat server '{}' at '{}' in Zookeeper", instanceId, serverAddress);
            startWatcher();
        } catch (Exception e) {
            log.error("Failed to register with Zookeeper", e);
        }
    }

    @PreDestroy
    public void deregister() {
        if (cache != null) cache.close();
        try {
            curator.delete().forPath(SERVERS_PATH + "/" + instanceId);
            log.info("Deregistered '{}' from Zookeeper", instanceId);
        } catch (Exception e) {
            log.warn("Could not deregister from Zookeeper: {}", e.getMessage());
        }
    }

    public List<String> getAvailableServers() {
        try {
            List<String> children = curator.getChildren().forPath(SERVERS_PATH);
            return children.stream().map(child -> {
                try {
                    byte[] data = curator.getData().forPath(SERVERS_PATH + "/" + child);
                    return new String(data, StandardCharsets.UTF_8);
                } catch (Exception e) { return null; }
            }).filter(addr -> addr != null).toList();
        } catch (Exception e) {
            log.error("Failed to fetch servers from Zookeeper", e);
            return List.of(serverAddress);
        }
    }

    private void startWatcher() {
        cache = CuratorCache.build(curator, SERVERS_PATH);
        cache.listenable().addListener((type, oldData, newData) ->
                log.info("Zookeeper event: {} | path={}", type,
                        newData != null ? newData.getPath() : (oldData != null ? oldData.getPath() : "?")));
        cache.start();
    }
}
