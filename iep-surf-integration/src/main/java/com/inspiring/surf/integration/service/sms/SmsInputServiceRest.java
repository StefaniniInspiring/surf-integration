package com.inspiring.surf.integration.service.sms;

import java.util.List;
import java.util.Map;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.inspiring.surf.integration.broker.BrokerMessageConfig;
import com.inspiring.surf.integration.util.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.inspiring.surf.integration.util.MapUtils.createMap;
import static javax.ws.rs.core.MediaType.*;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Component
@Path("sms")
public class SmsInputServiceRest {

    private static final Logger log = LoggerFactory.getLogger(SmsInputServiceRest.class);
    private static final Logger audit = LoggerFactory.getLogger("integration.audit.request");
    public static final String queueNameResponse = "surf.sms.response";
    public static final String queueNameStatus = "surf.sms.status";
    private @Autowired BrokerMessageConfig broker;

    @POST
    @Path("response")
    @Produces({TEXT_PLAIN})
    @Consumes(WILDCARD)
    public Response smsResponse(@Context HttpHeaders headers,
                             @Context UriInfo uriInfo,
                                @FormParam("celular") String msisdn,
                                @FormParam("mensagem") String text,
                                @FormParam("lashortnumber") String shortNumber,
                                @FormParam("seunum") String correlationId,
                                @FormParam("datastatus") String date) {

        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();

        log.info("SMS Response - Celular: {}, ShortNumber {}, SeuNum: {}, Texto: {}, Data: {}", msisdn, shortNumber, correlationId, text, date);

        Map<String, Object> request = createMap("text", text, "msisdn", msisdn, "shortNumber", shortNumber, "correlationId", correlationId, "date", date);

        audit.info("type={}|{}", queueNameResponse, MapUtils.toString(request, "|"));

        broker.sendMessage(queueNameResponse, request);

        return Response.ok("OK").build();
    }


    @POST
    @Path("status")
    @Produces(TEXT_PLAIN)
    @Consumes(WILDCARD)
    public Response smsStatusWithForm(@Context HttpHeaders headers,
                              @Context UriInfo uriInfo,
                              @FormParam("celular") String msisdn,
                              @FormParam("descricaostatus") String text,
                              @FormParam("status") String status,
                              @FormParam("SeuNum") String correlationId,
                              @FormParam("datastatus") String date) {

        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();

        log.info("SMS Status -  Celular: {}, Status {}, SeuNum: {}, Desc: {}, Data: {}", msisdn, status, correlationId, text, date);

        Map<String, Object> request = createMap("text", text, "msisdn", msisdn, "status", status, "text", text, "correlationId", correlationId, "date", date);

        audit.info("type={}|{}", queueNameResponse, MapUtils.toString(request, "|"));

        broker.sendMessage(queueNameStatus, request);

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

