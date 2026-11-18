package at.leoway.api;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/** The body of every validation 400, so the frontend handles one error format: {@code {"errors": [...]}}. */
public record Errors(List<String> errors) {
    public static BadRequestException badRequest(List<String> errors) {
        return new BadRequestException(Response.status(400).type(MediaType.APPLICATION_JSON)
                .entity(new Errors(errors)).build());
    }

    public static BadRequestException badRequest(String error) {
        return badRequest(List.of(error));
    }
}
