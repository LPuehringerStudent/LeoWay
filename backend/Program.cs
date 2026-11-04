using LeoWay.Api.Data;
using LeoWay.Api.Endpoints;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("LeoWay")
    ?? throw new InvalidOperationException("Set ConnectionStrings__LeoWay before starting the API.");

builder.Services.AddDbContext<LeoWayDbContext>(options => options.UseNpgsql(connectionString));
builder.Services.AddHealthChecks().AddDbContextCheck<LeoWayDbContext>("database");
builder.Services.AddOpenApi();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

// Register new feature endpoint groups here.
app.MapHealthEndpoints();
app.Run();
