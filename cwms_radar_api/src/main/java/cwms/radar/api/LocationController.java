package cwms.radar.api;

import static com.codahale.metrics.MetricRegistry.name;
import static cwms.radar.api.Controllers.CREATE;
import static cwms.radar.api.Controllers.DATUM;
import static cwms.radar.api.Controllers.DELETE;
import static cwms.radar.api.Controllers.FORMAT;
import static cwms.radar.api.Controllers.GET_ALL;
import static cwms.radar.api.Controllers.GET_ONE;
import static cwms.radar.api.Controllers.OFFICE;
import static cwms.radar.api.Controllers.RESULTS;
import static cwms.radar.api.Controllers.SIZE;
import static cwms.radar.api.Controllers.UNIT;
import static cwms.radar.api.Controllers.UPDATE;
import static cwms.radar.data.dao.JooqDao.getDslContext;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cwms.radar.api.enums.Nation;
import cwms.radar.api.enums.UnitSystem;
import cwms.radar.api.errors.NotFoundException;
import cwms.radar.api.errors.RadarError;
import cwms.radar.data.dao.LocationsDao;
import cwms.radar.data.dao.LocationsDaoImpl;
import cwms.radar.data.dto.Location;
import cwms.radar.formatters.ContentType;
import cwms.radar.formatters.Formats;
import cwms.radar.formatters.FormattingException;
import io.javalin.apibuilder.CrudHandler;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletResponse;
import org.geojson.FeatureCollection;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;


/**
 *
 */
public class LocationController implements CrudHandler {
    public static final Logger logger = Logger.getLogger(LocationController.class.getName());
    public static final String NAMES = "names";
    private final MetricRegistry metrics;

    private final Histogram requestResultSize;


    public LocationController(MetricRegistry metrics) {
        this.metrics = metrics;
        String className = this.getClass().getName();

        requestResultSize = this.metrics.histogram((name(className, RESULTS, SIZE)));
    }

    private Timer.Context markAndTime(String subject) {
        return Controllers.markAndTime(metrics, getClass().getName(), subject);
    }

    @OpenApi(
            queryParams = {
                    @OpenApiParam(name = NAMES, description = "Specifies the name(s) of the "
                            + "location(s) whose data is to be included in the response"),
                    @OpenApiParam(name = OFFICE, description = "Specifies the owning office of "
                            + "the location level(s) whose data is to be included in the response"
                            + ". If this field is not specified, matching location level "
                            + "information from all offices shall be returned."),
                    @OpenApiParam(name = UNIT, description = "Specifies the unit or unit system"
                            + " of the response. Valid values for the unit field are:\r\n 1. EN. "
                            + "  Specifies English unit system.  Location level values will be in"
                            + " the default English units for their parameters.\r\n2. SI.   "
                            + "Specifies the SI unit system.  Location level values will be in "
                            + "the default SI units for their parameters.\r\n3. Other. Any unit "
                            + "returned in the response to the units URI request that is "
                            + "appropriate for the requested parameters."),
                    @OpenApiParam(name = DATUM, description = "Specifies the elevation datum of"
                            + " the response. This field affects only elevation location levels. "
                            + "Valid values for this field are:\r\n1. NAVD88.  The elevation "
                            + "values will in the specified or default units above the NAVD-88 "
                            + "datum.\r\n2. NGVD29.  The elevation values will be in the "
                            + "specified or default units above the NGVD-29 datum."),
                    @OpenApiParam(name = FORMAT, description = "Specifies the encoding format "
                            + "of the response. Valid values for the format field for this URI "
                            + "are:\r\n1.    tab\r\n2.    csv\r\n3.    xml\r\n4.  wml2 (only if "
                            + "name field is specified)\r\n5.    json (default)\n" + "6.    "
                            + "geojson")
            },
            responses = {
                    @OpenApiResponse(status = "200",
                            content = {
                                    @OpenApiContent(type = Formats.JSON),
                                    @OpenApiContent(type = Formats.TAB),
                                    @OpenApiContent(type = Formats.CSV),
                                    @OpenApiContent(type = Formats.XML),
                                    @OpenApiContent(type = Formats.WML2),
                                    @OpenApiContent(type = Formats.GEOJSON),
                                    @OpenApiContent(type = "")
                            })
            },
            description = "Returns CWMS Location Data",
            tags = {"Locations"}
    )
    @Override
    public void getAll(Context ctx) {

        try (final Timer.Context timeContext = markAndTime(GET_ALL);
             DSLContext dsl = getDslContext(ctx)) {
            LocationsDao locationsDao = getLocationsDao(dsl);

            String names = ctx.queryParam(NAMES);
            String units = ctx.queryParam(UNIT);
            String datum = ctx.queryParam(DATUM);
            String office = ctx.queryParam(OFFICE);

            String formatParm = ctx.queryParamAsClass(FORMAT, String.class).getOrDefault("");
            String formatHeader = ctx.header(Header.ACCEPT);
            ContentType contentType = Formats.parseHeaderAndQueryParm(formatHeader, formatParm);
            ctx.contentType(contentType.toString());

            final String results;
            if (contentType.getType().equals(Formats.GEOJSON)) {
                FeatureCollection collection = locationsDao.buildFeatureCollection(names, units,
                        office);
                ctx.json(collection);

                requestResultSize.update(ctx.res.getBufferSize());
            } else {
                String format = getFormatFromContent(contentType);
                results = locationsDao.getLocations(names, format, units, datum, office);
                ctx.result(results);
                requestResultSize.update(results.length());
            }

            ctx.status(HttpServletResponse.SC_OK);

        } catch (Exception ex) {
            RadarError re = new RadarError("failed to process request");
            logger.log(Level.SEVERE, re.toString(), ex);
            ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).json(re);
        }
    }

    private String getFormatFromContent(ContentType contentType) {
        String format = "json";
        if (contentType != null) {
            // Seems weird to map back to format from contentType but we really want them to agree.
            // What if format wasn't provided but an accept header for csv was?
            // I think we would want to pass "csv" to the db procedure.
            Map<String, String> lookup = new LinkedHashMap<>();
            lookup.put(Formats.TAB, "tab");
            lookup.put(Formats.CSV, "csv");
            lookup.put(Formats.XML, "xml");
            lookup.put(Formats.WML2, "wml2");
            lookup.put(Formats.JSON, "json");

            String type = contentType.getType();
            if (lookup.containsKey(type)) {
                format = lookup.get(type);
            }
        }
        return format;
    }

    @OpenApi(
            queryParams = {
                    @OpenApiParam(name = OFFICE, required = true, description = "Specifies the "
                            + "owning office of the location level(s) whose data is to be "
                            + "included in the response. If this field is not specified, matching"
                            + " location level information from all offices shall be returned."),
                    @OpenApiParam(name = UNIT, description = "Specifies the unit or unit system"
                            + " of the response. Valid values for the unit field are:\r\n 1. EN. "
                            + "  Specifies English unit system.  Location values will be in the "
                            + "default English units for their parameters.\r\n2. SI.   Specifies "
                            + "the SI unit system.  Location values will be in the default SI "
                            + "units for their parameters.\r\n3. Other. Any unit returned in the "
                            + "response to the units URI request that is appropriate for the "
                            + "requested parameters.")
            },
            responses = {
                    @OpenApiResponse(status = "200",
                            content = {
                                    @OpenApiContent(type = Formats.JSONV2, from = Location.class),
                                    @OpenApiContent(type = Formats.XMLV2, from = Location.class)
                            }),
                    @OpenApiResponse(status = "404", description = "Based on the combination of "
                            + "inputs provided the location was not found.")
            },
            description = "Returns CWMS Location Data",
            tags = {"Locations"}
    )
    @Override
    public void getOne(Context ctx, @NotNull String name) {

        try (final Timer.Context timeContext = markAndTime(GET_ONE);
             DSLContext dsl = getDslContext(ctx)) {
            String units =
                    ctx.queryParamAsClass(UNIT, String.class).getOrDefault(UnitSystem.EN.value());
            String office = ctx.queryParam(OFFICE);
            String formatHeader = ctx.header(Header.ACCEPT) != null ? ctx.header(Header.ACCEPT) :
                    Formats.JSONV2;
            ContentType contentType = Formats.parseHeader(formatHeader);
            ctx.contentType(contentType.toString());
            LocationsDao locationDao = getLocationsDao(dsl);
            Location location = locationDao.getLocation(name, units, office);
            ObjectMapper om = getObjectMapperForFormat(contentType.getType());
            String serializedLocation = om.writeValueAsString(location);
            ctx.result(serializedLocation);
        } catch (NotFoundException e) {
            RadarError re = new RadarError("Not found.");
            logger.log(Level.WARNING, re.toString(), e);
            ctx.status(HttpServletResponse.SC_NOT_FOUND);
            ctx.json(re);
        } catch (IOException ex) {
            String errorMsg = "Error retrieving " + name;
            RadarError re = new RadarError(errorMsg);
            ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).json(re);
            logger.log(Level.SEVERE, errorMsg, ex);
        }
    }

    @OpenApi(
            requestBody = @OpenApiRequestBody(
                    content = {
                            @OpenApiContent(from = Location.class, type = Formats.JSON),
                            @OpenApiContent(from = Location.class, type = Formats.XML)
                    },
                    required = true),
            description = "Create new CWMS Location",
            method = HttpMethod.POST,
            path = "/locations",
            tags = {"Locations"}
    )
    @Override
    public void create(Context ctx) {

        try (final Timer.Context timeContext = markAndTime(CREATE);
             DSLContext dsl = getDslContext(ctx)) {
            LocationsDao locationsDao = getLocationsDao(dsl);

            String acceptHeader = ctx.req.getContentType();
            String formatHeader = acceptHeader != null ? acceptHeader : Formats.JSON;
            ContentType contentType = Formats.parseHeader(formatHeader);
            if (contentType == null) {
                throw new FormattingException("Format header could not be parsed");
            }
            Location locationFromBody = deserializeLocation(ctx.body(), contentType.getType());
            locationsDao.storeLocation(locationFromBody);
            ctx.status(HttpServletResponse.SC_ACCEPTED).json("Created Location");
        } catch (IOException ex) {
            RadarError re = new RadarError("failed to process request");
            logger.log(Level.SEVERE, re.toString(), ex);
            ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).json(re);
        }
    }

    @OpenApi(
            requestBody = @OpenApiRequestBody(
                    content = {
                            @OpenApiContent(from = Location.class, type = Formats.JSON),
                            @OpenApiContent(from = Location.class, type = Formats.XML)
                    },
                    required = true),
            description = "Update CWMS Location",
            method = HttpMethod.PATCH,
            path = "/locations",
            tags = {"Locations"},
            responses = {
                    @OpenApiResponse(status = "404", description = "Based on the combination of "
                            + "inputs provided the location was not found.")
            }
    )
    @Override
    public void update(Context ctx, @NotNull String locationId) {

        try (final Timer.Context timeContext = markAndTime(UPDATE);
             DSLContext dsl = getDslContext(ctx)) {
            LocationsDao locationsDao = getLocationsDao(dsl);

            String acceptHeader = ctx.req.getContentType();
            String formatHeader = acceptHeader != null ? acceptHeader : Formats.JSON;
            ContentType contentType = Formats.parseHeader(formatHeader);
            if (contentType == null) {
                throw new FormattingException("Format header could not be parsed");
            }
            Location locationFromBody = deserializeLocation(ctx.body(), contentType.getType());
            //getLocation will throw an error if location does not exist
            Location existingLocation = locationsDao.getLocation(locationId,
                    UnitSystem.EN.getValue(), locationFromBody.getOfficeId());
            existingLocation = updatedClearedFields(ctx.body(), contentType.getType(),
                    existingLocation);
            //only store (update) if location does exist
            Location updatedLocation = getUpdatedLocation(existingLocation, locationFromBody);
            if (!updatedLocation.getName().equalsIgnoreCase(existingLocation.getName())) {
                //if name changed then delete location with old name
                locationsDao.renameLocation(locationId, updatedLocation);
                ctx.status(HttpServletResponse.SC_ACCEPTED).json("Updated and renamed Location");
            } else {
                locationsDao.storeLocation(updatedLocation);
                ctx.status(HttpServletResponse.SC_ACCEPTED).json("Updated Location");
            }
        } catch (NotFoundException e) {
            RadarError re = new RadarError("Not found.");
            logger.log(Level.WARNING, re.toString(), e);
            ctx.status(HttpServletResponse.SC_NOT_FOUND);
            ctx.json(re);
        } catch (IOException ex) {
            RadarError re =
                    new RadarError("Failed to process request: " + ex.getLocalizedMessage());
            logger.log(Level.SEVERE, re.toString(), ex);
            ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).json(re);
        }

    }

    @OpenApi(
            queryParams = {
                    @OpenApiParam(name = OFFICE, description = "Specifies the owning office of "
                            + "the location whose data is to be deleted. If this field is not "
                            + "specified, matching location information will be deleted from all "
                            + "offices.")
            },
            description = "Delete CWMS Location",
            method = HttpMethod.DELETE,
            path = "/locations",
            tags = {"Locations"},
            responses = {
                    @OpenApiResponse(status = "404", description = "Based on the combination of "
                            + "inputs provided the location was not found.")
            }
    )
    @Override
    public void delete(Context ctx, @NotNull String locationId) {

        try (final Timer.Context timeContext = markAndTime(DELETE);
             DSLContext dsl = getDslContext(ctx)) {
            String office = ctx.queryParam(OFFICE);
            LocationsDao locationsDao = getLocationsDao(dsl);
            locationsDao.deleteLocation(locationId, office);
            ctx.status(HttpServletResponse.SC_ACCEPTED).json(locationId + " Deleted");
        } catch (NotFoundException e) {
            RadarError re = new RadarError("Not found.");
            logger.log(Level.WARNING, re.toString(), e);
            ctx.status(HttpServletResponse.SC_NOT_FOUND);
            ctx.json(re);
        } catch (IOException ex) {
            RadarError re = new RadarError("Failed to delete location");
            logger.log(Level.SEVERE, re.toString(), ex);
            ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).json(re);
        }
    }

    public static LocationsDao getLocationsDao(DSLContext dsl) {
        return new LocationsDaoImpl(dsl);
    }


    private Location updatedClearedFields(String body, String format, Location existingLocation)
            throws IOException {
        ObjectMapper om = getObjectMapperForFormat(format);
        JsonNode root = om.readTree(body);
        JavaType javaType = om.getTypeFactory().constructType(Location.class);
        BeanDescription beanDescription = om.getSerializationConfig().introspect(javaType);
        List<BeanPropertyDefinition> properties = beanDescription.findProperties();
        Location retVal = new Location.Builder(existingLocation).build();
        try {
            for (BeanPropertyDefinition propertyDefinition : properties) {
                String propertyName = propertyDefinition.getName();
                JsonNode propertyValue = root.findValue(propertyName);
                if (propertyValue != null && "".equals(propertyValue.textValue())) {
                    retVal = new Location.Builder(retVal)
                            .withProperty(propertyName, null)
                            .build();
                }
            }
        } catch (NullPointerException e) {
            //gets thrown if required field is null
            throw new IOException(e.getMessage());
        }
        return retVal;
    }

    public static Location deserializeLocation(String body, String format)
            throws IOException {
        ObjectMapper om = getObjectMapperForFormat(format);
        Location retVal;
        try {
            retVal = new Location.Builder(om.readValue(body, Location.class)).build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to deserialize location", e);
            throw new IOException("Failed to deserialize location");
        }
        return retVal;
    }

    private static ObjectMapper getObjectMapperForFormat(String format) {
        ObjectMapper om;
        if ((Formats.XML).equals(format) || (Formats.XMLV2).equals(format)) {
            om = new XmlMapper();
        } else if (Formats.JSON.equals(format) || (Formats.JSONV2).equals(format)) {
            om = new ObjectMapper();
        } else {
            throw new FormattingException("Format is not currently supported for Locations");
        }
        om.registerModule(new JavaTimeModule());
        return om;
    }

    private Location getUpdatedLocation(Location existingLocation, Location updatedLocation) {
        String updatedName = updatedLocation.getName() == null
                ? existingLocation.getName() : updatedLocation.getName();
        Double updatedLatitude = updatedLocation.getLatitude() == null
                ? existingLocation.getLatitude() : updatedLocation.getLatitude();
        Double updatedLongitude = updatedLocation.getLongitude() == null
                ? existingLocation.getLongitude() : updatedLocation.getLongitude();
        Boolean updatedIsActive = updatedLocation.getActive() == null
                ? existingLocation.getActive() : updatedLocation.getActive();
        String updatedPublicName = updatedLocation.getPublicName() == null
                ? existingLocation.getPublicName() : updatedLocation.getPublicName();
        String updatedLongName = updatedLocation.getLongName() == null
                ? existingLocation.getLongName() : updatedLocation.getLongName();
        String updatedDescription = updatedLocation.getDescription() == null
                ? existingLocation.getDescription() : updatedLocation.getDescription();
        String updatedTimeZoneId = updatedLocation.getTimezoneName() == null
                ? existingLocation.getTimezoneName() : updatedLocation.getTimezoneName();
        String updatedLocationType = updatedLocation.getLocationType() == null
                ? existingLocation.getLocationType() : updatedLocation.getLocationType();
        String updatedLocationKind = updatedLocation.getLocationKind() == null
                ? existingLocation.getLocationKind() : updatedLocation.getLocationKind();
        Nation updatedNation = updatedLocation.getNation() == null
                ? existingLocation.getNation() : updatedLocation.getNation();
        String updatedStateInitial = updatedLocation.getStateInitial() == null
                ? existingLocation.getStateInitial() : updatedLocation.getStateInitial();
        String updatedCountyName = updatedLocation.getCountyName() == null
                ? existingLocation.getCountyName() : updatedLocation.getCountyName();
        String updatedNearestCity = updatedLocation.getNearestCity() == null
                ? existingLocation.getNearestCity() : updatedLocation.getNearestCity();
        String updatedHorizontalDatum = updatedLocation.getHorizontalDatum() == null
                ? existingLocation.getHorizontalDatum() : updatedLocation.getHorizontalDatum();
        Double updatedPublishedLongitude = updatedLocation.getPublishedLongitude() == null
                ? existingLocation.getPublishedLongitude() : updatedLocation.getPublishedLongitude();
        Double updatedPublishedLatitude = updatedLocation.getPublishedLatitude() == null
                ? existingLocation.getPublishedLatitude() : updatedLocation.getPublishedLatitude();
        String updatedVerticalDatum = updatedLocation.getVerticalDatum() == null
                ? existingLocation.getVerticalDatum() : updatedLocation.getVerticalDatum();
        Double updatedElevation = updatedLocation.getElevation() == null
                ? existingLocation.getElevation() : updatedLocation.getElevation();
        String updatedMapLabel = updatedLocation.getMapLabel() == null
                ? existingLocation.getMapLabel() : updatedLocation.getMapLabel();
        String updatedBoundingOfficeId = updatedLocation.getBoundingOfficeId() == null
                ? existingLocation.getBoundingOfficeId() : updatedLocation.getBoundingOfficeId();
        String updatedOfficeId = updatedLocation.getOfficeId() == null
                ? existingLocation.getOfficeId() : updatedLocation.getOfficeId();
        return new Location.Builder(updatedName, updatedLocationKind,
                ZoneId.of(updatedTimeZoneId), updatedLatitude, updatedLongitude,
                updatedHorizontalDatum, updatedOfficeId)
                .withActive(updatedIsActive)
                .withPublicName(updatedPublicName)
                .withLongName(updatedLongName)
                .withDescription(updatedDescription)
                .withLocationType(updatedLocationType)
                .withNation(updatedNation)
                .withStateInitial(updatedStateInitial)
                .withCountyName(updatedCountyName)
                .withNearestCity(updatedNearestCity)
                .withPublishedLongitude(updatedPublishedLongitude)
                .withPublishedLatitude(updatedPublishedLatitude)
                .withVerticalDatum(updatedVerticalDatum)
                .withElevation(updatedElevation)
                .withMapLabel(updatedMapLabel)
                .withBoundingOfficeId(updatedBoundingOfficeId)
                .build();
    }

}
