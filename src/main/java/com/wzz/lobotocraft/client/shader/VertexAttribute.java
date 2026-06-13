package com.wzz.lobotocraft.client.shader;

public abstract class VertexAttribute<T> implements IVertexOperation {

    private final AttributeKey<T> key;
    /**
     * Set to true when the attribute is part of the pipeline. Should only be managed by CCRenderState when constructing the pipeline
     */
    public boolean active = false;

    public VertexAttribute(AttributeKey<T> key) {
        this.key = key;
    }

    @Override
    public int operationID() {
        return key.operationIndex;
    }
}
