package at.leoway.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/api")
public class LeoWayApplication extends Application {
    // Add feature resources with @Path. Quarkus registers them under /api.
}
