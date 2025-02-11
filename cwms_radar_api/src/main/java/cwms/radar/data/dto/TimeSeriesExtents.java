package cwms.radar.data.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import cwms.radar.data.dto.TimeSeries.Record;
import cwms.radar.formatters.xml.adapters.ZonedDateTimeAdapter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "extents")
@Schema(description = "TimeSeries extent information")
@XmlSeeAlso(Record.class)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
@JsonPropertyOrder(alphabetic = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
public class TimeSeriesExtents {

    @XmlJavaTypeAdapter(ZonedDateTimeAdapter.class)
    @Schema(description = "TimeSeries version to which this extent information applies")
    @JsonFormat(shape = Shape.STRING)
    ZonedDateTime versionTime;

    @XmlJavaTypeAdapter(ZonedDateTimeAdapter.class)
    @Schema(description = "Earliest value in the timeseries")
    @JsonFormat(shape = Shape.STRING)
    @XmlElement(name = "earliest-time")
    ZonedDateTime earliestTime;

    @XmlJavaTypeAdapter(ZonedDateTimeAdapter.class)
    @Schema(description = "Latest value in the timeseries")
    @JsonFormat(shape = Shape.STRING)
    @XmlElement(name = "latest-time")
    ZonedDateTime latestTime;

    @XmlJavaTypeAdapter(ZonedDateTimeAdapter.class)
    @Schema(description = "Last update in the timeseries")
    @JsonFormat(shape = Shape.STRING)
    @XmlElement(name = "last-update")
    ZonedDateTime lastUpdate;

    @SuppressWarnings("unused") // required so JAXB can initialize and marshal
    private TimeSeriesExtents() {
    }

    public TimeSeriesExtents(final ZonedDateTime versionTime, final ZonedDateTime earliestTime,
                             final ZonedDateTime latestTime, final ZonedDateTime lastUpdateTime) {
        this.versionTime = versionTime;
        this.earliestTime = earliestTime;
        this.latestTime = latestTime;
        this.lastUpdate = lastUpdateTime;
    }

    public TimeSeriesExtents(final Timestamp versionTime, final Timestamp earliestTime,
                             final Timestamp latestTime, final Timestamp lastUpdateTime) {
        this(toZdt(versionTime), toZdt(earliestTime), toZdt(latestTime), toZdt(lastUpdateTime));
    }

    private static ZonedDateTime toZdt(final Timestamp time) {
        if (time != null) {
            return ZonedDateTime.ofInstant(time.toInstant(), ZoneId.of("UTC"));
        } else {
            return null;
        }
    }

    public ZonedDateTime getVersionTime() {
        return this.versionTime;
    }

    public ZonedDateTime getEarliestTime() {
        return this.earliestTime;
    }

    public ZonedDateTime getLatestTime() {
        return this.latestTime;
    }

    public ZonedDateTime getLastUpdate() {
        return this.lastUpdate;
    }

}
