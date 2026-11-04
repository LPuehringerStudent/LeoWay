using Microsoft.EntityFrameworkCore;

namespace LeoWay.Api.Data;

public sealed class LeoWayDbContext(DbContextOptions<LeoWayDbContext> options) : DbContext(options)
{
    // Add domain DbSets and their mappings here when implementing issue #18.
}
