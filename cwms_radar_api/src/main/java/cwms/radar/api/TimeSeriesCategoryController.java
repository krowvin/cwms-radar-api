package cwms.radar.api;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import cwms.radar.data.CwmsDataManager;
import cwms.radar.data.dto.TimeSeriesCategory;
import cwms.radar.formatters.ContentType;
import cwms.radar.formatters.Formats;
import io.javalin.apibuilder.CrudHandler;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;

import static com.codahale.metrics.MetricRegistry.name;

public class TimeSeriesCategoryController implements CrudHandler
{
	public static final Logger logger = Logger.getLogger(TimeSeriesCategoryController.class.getName());

	private final MetricRegistry metrics;
	private final Meter getAllRequests;
	private final Timer getAllRequestsTime;
	private final Meter getOneRequest;
	private final Timer getOneRequestTime;
	private final Histogram requestResultSize;

	public TimeSeriesCategoryController(MetricRegistry metrics)
	{
		this.metrics = metrics;
		String className = this.getClass().getName();
		getAllRequests = this.metrics.meter(name(className, "getAll", "count"));
		getAllRequestsTime = this.metrics.timer(name(className, "getAll", "time"));
		getOneRequest = this.metrics.meter(name(className, "getOne", "count"));
		getOneRequestTime = this.metrics.timer(name(className, "getOne", "time"));
		requestResultSize = this.metrics.histogram((name(className, "results", "size")));
	}

	@OpenApi(queryParams = {
			@OpenApiParam(name = "office", description = "Specifies the owning office of the timeseries category(ies) whose data is to be included in the response. If this field is not specified, matching timeseries category information from all offices shall be returned."),
			},
			responses = {@OpenApiResponse(status = "200",
					content = {@OpenApiContent(isArray = true, from = TimeSeriesCategory.class, type = Formats.JSON)
					//							@OpenApiContent(isArray = true, from = TabV1TimeSeriesCategory.class, type = Formats.TAB ),
					//							@OpenApiContent(isArray = true, from = CsvV1TimeSeriesCategory.class, type = Formats.CSV )
			}
			),
					@OpenApiResponse(status = "404", description = "Based on the combination of inputs provided the categories were not found."),
					@OpenApiResponse(status = "501", description = "request format is not implemented")}, description = "Returns CWMS timeseries category Data", tags = {"TimeSeries Categories"})
	@Override
	public void getAll(Context ctx)
	{
		getAllRequests.mark();
		try(final Timer.Context timeContext = getAllRequestsTime.time(); CwmsDataManager cdm = new CwmsDataManager(ctx))
		{
			String office = ctx.queryParam("office");

			List<TimeSeriesCategory> cats = cdm.getTimeSeriesCategories(office);

			String formatHeader = ctx.header(Header.ACCEPT);
			ContentType contentType = Formats.parseHeaderAndQueryParm(formatHeader, "json");

			String result = Formats.format(contentType,cats);

			ctx.result(result).contentType(contentType.toString());
			requestResultSize.update(result.length());

			ctx.status(HttpServletResponse.SC_OK);
		}
		catch(SQLException ex)
		{
			logger.log(Level.SEVERE, null, ex);
			ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			ctx.result("Failed to process request");
		}
	}

	@OpenApi(
			pathParams = {
					@OpenApiParam(name = "category-id", required = true, description = "Specifies the Category whose data is to be included in the response."),
			},
			queryParams = {
			@OpenApiParam(name = "office", required = true, description = "Specifies the owning office of the timeseries category whose data is to be included in the response."),
			},

			responses = {
					@OpenApiResponse(status = "200",
							content = {
							@OpenApiContent(from = TimeSeriesCategory.class, type = Formats.JSON)
//							@OpenApiContent(from = TabV1TimeSeriesCategory.class, type = Formats.TAB ),
//							@OpenApiContent(from = CsvV1TimeSeriesCategory.class, type = Formats.CSV )
					}
					),
					@OpenApiResponse(status = "404", description = "Based on the combination of inputs provided the timeseries category was not found."),
					@OpenApiResponse(status = "501", description = "request format is not implemented")},
			description = "Retrieves requested timeseries category", tags = {"TimeSeries Categories"})
	@Override
	public void getOne(Context ctx, String categoryId)
	{
		getOneRequest.mark();
		try(final Timer.Context timeContext = getOneRequestTime.time(); CwmsDataManager cdm = new CwmsDataManager(ctx))
		{
			String office = ctx.queryParam("office");

			String formatHeader = ctx.header(Header.ACCEPT);
			ContentType contentType = Formats.parseHeaderAndQueryParm(formatHeader, "json");

			TimeSeriesCategory grp = cdm.getTimeSeriesCategory(office, categoryId);

			String result = Formats.format(contentType,grp);

			ctx.result(result).contentType(contentType.toString());
			requestResultSize.update(result.length());

			ctx.status(HttpServletResponse.SC_OK);
		}
		catch(SQLException ex)
		{
			logger.log(Level.SEVERE, null, ex);
			ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			ctx.result("Failed to process request");
		}
	}

	@OpenApi(ignore = true)
	@Override
	public void create(Context ctx)
	{
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@OpenApi(ignore = true)
	@Override
	public void update(Context ctx, String locationCode)
	{
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@OpenApi(ignore = true)
	@Override
	public void delete(Context ctx, String locationCode)
	{
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
}
