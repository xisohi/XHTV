package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Proxy implements Process {

    private static final String INVALID_RESPONSE = "Invalid proxy response";

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/proxy");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            params.putAll(session.getHeaders());
            params.putAll(files);
            return createResponse(BaseLoader.get().proxy(params));
        } catch (Throwable e) {
            e.printStackTrace();
            return Nano.error(Objects.toString(e.getMessage(), e.toString()));
        }
    }

    private static Response createResponse(Object[] rs) {
        if (rs == null || rs.length == 0) return Nano.error(INVALID_RESPONSE);
        if (rs[0] instanceof Response response) return response;
        if (rs.length < 3 || !(rs[0] instanceof Integer code) || !(rs[2] instanceof InputStream stream)) return Nano.error(INVALID_RESPONSE);
        Response response = NanoHTTPD.newChunkedResponse(toStatus(code), Objects.toString(rs[1], null), stream);
        if (rs.length > 3) addHeaders(response, rs[3]);
        return response;
    }

    private static void addHeaders(Response response, Object headers) {
        if (!(headers instanceof Map<?, ?> map)) return;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            response.addHeader(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    private static IStatus toStatus(int code) {
        Status status = Status.lookup(code);
        return status != null ? status : code >= 100 && code <= 599 ? new ProxyStatus(code) : Status.INTERNAL_ERROR;
    }

    private record ProxyStatus(int code) implements IStatus {

        @Override
        public String getDescription() {
            return code + " Proxy Status";
        }

        @Override
        public int getRequestStatus() {
            return code;
        }
    }
}
