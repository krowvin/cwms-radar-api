package cwms.radar.data.dao;

import static cwms.radar.data.dao.DaoTest.getDslContext;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import cwms.radar.data.dto.rating.RatingTemplate;
import fixtures.RadarApiSetupCallback;
import hec.data.RatingException;
import hec.data.cwmsRating.RatingSet;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.function.Consumer;
import mil.army.usace.hec.test.database.CwmsDatabaseContainer;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("integration")
@ExtendWith(RadarApiSetupCallback.class)
class RatingTemplateDaoTestIT {


    @Test
    void testRetrieveRatingTemplates() throws SQLException, JsonProcessingException {

        CwmsDatabaseContainer databaseLink = RadarApiSetupCallback.getDatabaseLink();
        databaseLink.connection((Consumer<Connection>) c -> {//
            testRetrieveRatingTemplate(c, databaseLink.getOfficeId());
        });
    }


//    // This is how the test can be run from a unit test.
//    // Must have existing database and specify the -D connection args
//    // It takes 4.2 seconds to run it this way.
//    @Test
//    void testRetrieveRatingTemplates() throws SQLException, JsonProcessingException
//    {
//        Connection c = getConnection();
//        testRetrieveRatingTemplate(c, "SWT");
//    }


    void testRetrieveRatingTemplate(Connection c, String connectionOfficeId) {
        try (DSLContext context = getDslContext(c, connectionOfficeId)) {

            String filename = "ARBU.Elev_Stor.Linear.Production.xml.gz";
            String resource = "cwms/radar/data/dao/" + filename;
            storeRatingSet(c, resource);

            String officeId = "SWT";
            RatingTemplateDao dao = new RatingTemplateDao(context);
            Set<RatingTemplate> ratingTemplates = dao.retrieveRatingTemplates(officeId,
                    "Elev;Stor.Linear");
            assertNotNull(ratingTemplates);
            assertFalse(ratingTemplates.isEmpty());

            // The ARBU rating template looks like:
            //  <rating-template office-id="SWT">
            //  <parameters-id>Elev;Stor</parameters-id>
            //  <version>Linear</version>
            //  <ind-parameter-specs>
            //   <ind-parameter-spec position="1">
            //    <parameter>Elev</parameter>
            //    <in-range-method>LINEAR</in-range-method>
            //    <out-range-low-method>NEAREST</out-range-low-method>
            //    <out-range-high-method>NEAREST</out-range-high-method>
            //   </ind-parameter-spec>
            //  </ind-parameter-specs>
            //  <dep-parameter>Stor</dep-parameter>
            //  <description></description>
            // </rating-template>

            boolean allSWT = ratingTemplates.stream().allMatch(rt ->
                    "SWT".equals(rt.getOfficeId())
                    && "Linear".equals(rt.getVersion() )
                    && "Stor".equals(rt.getDependentParameter())
                    && "Elev".equals(rt.getIndependentParameterSpecs().get(0).getParameter())
            );
            assertTrue(allSWT);

        } catch (RatingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void storeRatingSet(Connection c, String resource) throws IOException,
            RatingException {
        String xmlRating = JsonRatingUtilsTest.loadResourceAsString(resource);
        // make sure we got something.
        assertNotNull(xmlRating);

        // make sure we can parse it.
        RatingSet ratingSet = RatingSet.fromXml(xmlRating);
        assertNotNull(ratingSet);

        ratingSet.storeToDatabase(c, true) ;
    }


}