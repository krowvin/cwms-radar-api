package cwms.radar.api;

import static com.codahale.metrics.MetricRegistry.name;
import static cwms.radar.api.Controllers.FORMAT;
import static cwms.radar.api.Controllers.GET_ALL;
import static cwms.radar.api.Controllers.GET_ONE;
import static cwms.radar.api.Controllers.RESULTS;
import static cwms.radar.api.Controllers.SIZE;
import static cwms.radar.data.dao.JooqDao.getDslContext;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import cwms.radar.api.errors.RadarError;
import cwms.radar.data.dao.ParameterDao;
import cwms.radar.formatters.Formats;
import io.javalin.apibuilder.CrudHandler;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletResponse;
import org.jooq.DSLContext;

public class ParametersController implements CrudHandler {
    private static final Logger logger = Logger.getLogger(ParametersController.class.getName());
    private final MetricRegistry metrics;

    private final Histogram requestResultSize;

    public ParametersController(MetricRegistry metrics) {
        this.metrics = metrics;
        String className = this.getClass().getName();

        requestResultSize = this.metrics.histogram((name(className, RESULTS, SIZE)));
    }

    private Timer.Context markAndTime(String subject) {
        return Controllers.markAndTime(metrics, getClass().getName(), subject);
    }

    @OpenApi(ignore = true)
    @Override
    public void create(Context ctx) {
        ctx.status(HttpServletResponse.SC_NOT_FOUND);
    }

    @OpenApi(ignore = true)
    @Override
    public void delete(Context ctx, String id) {
        ctx.status(HttpServletResponse.SC_NOT_FOUND);

    }

    @OpenApi(
            queryParams = {
                    @OpenApiParam(name = FORMAT, required = false, description = "Specifies the"
                            + " encoding format of the response. Valid value for the format field"
                            + " for this URI are:\r\n1. tab\r\n2. csv\r\n 3. xml\r\n4. json "
                            + "(default)")
            },
            responses = {
                    @OpenApiResponse(status = "200")
            },
            tags = {"Parameters"}
    )
    @Override
    public void getAll(Context ctx) {
        try (
                final Timer.Context timeContext = markAndTime(GET_ALL);
                DSLContext dsl = getDslContext(ctx)
        ) {
            ParameterDao dao = new ParameterDao(dsl);
            String format = ctx.queryParamAsClass(FORMAT, String.class).getOrDefault("json");

            switch (format) {
                case "json": {
                    ctx.contentType(Formats.JSON);
                    break;
                }
                case "tab": {
                    ctx.contentType(Formats.TAB);
                    break;
                }
                case "csv": {
                    ctx.contentType(Formats.CSV);
                    break;
                }
                case "xml": {
                    ctx.contentType(Formats.XML);
                    break;
                }
                case "wml2": {
                    ctx.contentType(Formats.WML2);
                    break;
                }
                default:
                    ctx.status(HttpServletResponse.SC_NOT_IMPLEMENTED)
                            .json(RadarError.notImplemented());
                    return;
            }

            String results = dao.getParameters(format);
            ctx.status(HttpServletResponse.SC_OK);
            ctx.result(results);
            requestResultSize.update(results.length());
        } catch (Exception ex) {
            RadarError re = new RadarError("Failed to process request");
            logger.log(Level.SEVERE, re.toString(), ex);
            ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).json(re);
        }
    }

    @OpenApi(ignore = true)
    @Override
    public void getOne(Context ctx, String id) {
        try (final Timer.Context timeContext = markAndTime(GET_ONE)) {
            ctx.status(HttpServletResponse.SC_NOT_IMPLEMENTED).json(RadarError.notImplemented());
        }
    }

    @OpenApi(ignore = true)
    @Override
    public void update(Context ctx, String id) {
        ctx.status(HttpServletResponse.SC_NOT_IMPLEMENTED).json(RadarError.notImplemented());

    }

}
