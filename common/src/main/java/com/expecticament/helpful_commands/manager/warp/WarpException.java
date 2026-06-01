package com.expecticament.helpful_commands.manager.warp;

public class WarpException {
    public static class AlreadyExists extends RuntimeException {
        public AlreadyExists(String name) {
            super("A warp named '%s' already exists.".formatted(name));
        }
    }

    public static class DoesntExist extends RuntimeException {
        public DoesntExist(String name) {
            super("No warp found with the name '%s'.".formatted(name));
        }
    }

    public static class SameName extends RuntimeException {
        public SameName(String name) {
            super("The warp is already named '%s'.".formatted(name));
        }
    }
}
