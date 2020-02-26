package com.inspiring.surf.integration.service.sms;

import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.inspiring.surf.integration.broker.BrokerMessageConfig;
import com.inspiring.surf.integration.util.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.inspiring.surf.integration.util.MapUtils.createMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
@Path("sms")
public class SmsInputServiceRest {

    private static final Logger log = LoggerFactory.getLogger(SmsInputServiceRest.class);
    private static final Logger audit = LoggerFactory.getLogger("integration.audit.request");
    private static final String queueName = "surf.sms.input";
    private @Autowired BrokerMessageConfig broker;

    @GET
    @Path("response")
    @Produces({TEXT_PLAIN})
    public Response smsInput(@Context HttpHeaders headers,
                             @Context UriInfo uriInfo) {

        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        log.debug("SMS Input - Query Parameters: {}", queryParameters);

        String text = getParameterValue("Texto", queryParameters);
        if (isBlank(text)) {
            return Response.status(400).build();
        }
        String msisdn = getParameterValue("Celular", queryParameters);
        if (isBlank(msisdn)) {
            return Response.status(400).build();
        }
        String shortNumber = getParameterValue("ShortCode", queryParameters);
        shortNumber = defaultIfBlank(shortNumber, "");

        String correlationId = getParameterValue("Seunum", queryParameters);
        correlationId = defaultIfBlank(correlationId, "");

        String date = getParameterValue("Data", queryParameters);
        date = defaultIfBlank(date, "");

        Map<String, Object> request = createMap("text", text, "msisdn", msisdn, "shortNumber", shortNumber, "correlationId", correlationId, "date", date);

        audit.info("type={}|{}", queueName, MapUtils.toString(request, "|"));

        broker.sendMessage(queueName, request);

        return Response.ok("OK").build();
    }

    @POST
    @Path("status")
    @Consumes({APPLICATION_XML, APPLICATION_JSON, TEXT_XML})
    public Response smsCallback(@Context HttpHeaders headers,
                                @Context UriInfo uriInfo) {

        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        log.debug("SMS Callback - Query Parameters: {}, MSEResponse: {}", queryParameters);

        return Response.ok().build();
    }

    private String getParameterValue(String name, MultivaluedMap<String, String> queryParameters) {
        Map.Entry<String, List<String>> entry = queryParameters.entrySet()
                .stream()
                .filter(key -> key.getKey().equalsIgnoreCase(name))
                .findFirst().orElse(null);

        if (entry != null && isNotEmpty(entry.getValue())) {
            return entry.getValue().get(0);
        }
        return null;
    }
}

