package com.biffis.tracker;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class TrackerApplication {

    public static void main(String[] args) {
        // `set-password <email>` runs as a web-less one-shot so it doesn't
        // try to bind port 8080 (e.g. when docker-exec'd into the running
        // container). See cli/SetPasswordRunner.
        WebApplicationType type = (args.length > 0 && "set-password".equals(args[0]))
                ? WebApplicationType.NONE
                : WebApplicationType.SERVLET;

        new SpringApplicationBuilder(TrackerApplication.class)
                .web(type)
                .run(args);
    }
}
