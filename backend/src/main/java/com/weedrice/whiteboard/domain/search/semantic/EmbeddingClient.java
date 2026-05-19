package com.weedrice.whiteboard.domain.search.semantic;

public interface EmbeddingClient {
    boolean isAvailable();

    float[] embed(String input);

    String model();
}
