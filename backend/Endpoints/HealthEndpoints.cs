using Microsoft.AspNetCore.Diagnostics.HealthChecks;

namespace LeoWay.Api.Endpoints;

public static class HealthEndpoints
{
    public static void MapHealthEndpoints(this WebApplication app)
    {
        app.MapHealthChecks("/health/live", new HealthCheckOptions
        {
            Predicate = _ => false
        });

        app.MapHealthChecks("/api/health/ready");
    }
}
