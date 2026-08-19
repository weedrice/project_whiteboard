package com.weedrice.whiteboard.domain.post.service;

record PostMediaCandidate(Type type, String url) {

    enum Type {
        IMAGE,
        VIDEO
    }
}
