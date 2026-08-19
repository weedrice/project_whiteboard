package com.weedrice.whiteboard.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.async")
public class AsyncTaskProperties {

    private Pool durable = new Pool(4, 8, 200);
    private Pool notification = new Pool(4, 8, 200);
    private Pool stream = new Pool(1, 2, 50);
    private Pool observability = new Pool(2, 4, 100);
    private int awaitTerminationSeconds = 30;

    @Getter
    @Setter
    public static class Pool {
        private int coreSize;
        private int maxSize;
        private int queueCapacity;

        public Pool() {
        }

        public Pool(int coreSize, int maxSize, int queueCapacity) {
            this.coreSize = coreSize;
            this.maxSize = maxSize;
            this.queueCapacity = queueCapacity;
        }
    }
}
