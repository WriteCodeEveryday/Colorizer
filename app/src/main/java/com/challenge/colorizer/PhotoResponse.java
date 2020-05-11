package com.challenge.colorizer;

public class PhotoResponse {
    private int status;
    private BodyResponse body;

    public String[] getPayload() {
        if (body != null) {
            return body.getPayload();
        }
        return new String[0];
    }
}

class BodyResponse {
    private int status;
    private ObjectResponse[] objects;

    public String[] getPayload() {
        if (objects.length > 0) {
            return objects[0].getData();
        }
        return new String[0];
    };
}

class ObjectResponse {
    private int status;
    private String name;
    private String colorized;
    private String colorized_improved;
    private String improved;

    public String[] getData() {
        return new String[] { improved, colorized_improved };
    }
}
