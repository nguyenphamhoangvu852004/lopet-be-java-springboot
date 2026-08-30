package com.nguyenvu.lopet.security.rebac;

public record ObjectRef(String type, Integer id) {

    public static final String PLATFORM = "platform";
    public static final String ACCOUNT = "account";
    public static final String POST = "post";
    public static final String COMMENT = "comment";
    public static final String MESSAGE = "message";

    public ObjectRef {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("ObjectRef thiếu type");
        }
    }

    public static ObjectRef platform() {
        return new ObjectRef(PLATFORM, null);
    }

    public static ObjectRef account(Integer id) {
        return new ObjectRef(ACCOUNT, id);
    }

    public static ObjectRef post(Integer id) {
        return new ObjectRef(POST, id);
    }

    public static ObjectRef comment(Integer id) {
        return new ObjectRef(COMMENT, id);
    }

    public static ObjectRef message(Integer id) {
        return new ObjectRef(MESSAGE, id);
    }

    public boolean isPlatform() {
        return PLATFORM.equals(type);
    }

    @Override
    public String toString() {
        return id == null ? type + ":lopet" : type + ":" + id;
    }
}
