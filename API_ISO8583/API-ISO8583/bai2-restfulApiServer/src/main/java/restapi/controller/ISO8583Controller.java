package restapi.controller;


import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;
import org.jpos.iso.packager.ISO87APackager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import restapi.service.ISO8583Service;

import javax.servlet.http.HttpServletRequest;
import java.io.*;

@RestController
@RequestMapping("/api/iso8583/v2")
public class ISO8583Controller {

    @Autowired
    private ISO8583Service iso8583Service;

    @PostMapping(
        consumes= MediaType.APPLICATION_OCTET_STREAM_VALUE,
        produces= MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public byte[] handleIso(HttpServletRequest request) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try(InputStream in = request.getInputStream()){
            byte[] buf = new byte[4096];
            int len;
            while((len=in.read(buf))!=-1){
                baos.write(buf,0,len);
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        byte[] isoBytes = baos.toByteArray();

        try {
            ISOMsg isoReq = new ISOMsg();
            isoReq.setPackager(new ISO87APackager());
            isoReq.unpack(isoBytes);

            ISOMsg isoResp = iso8583Service.processIso(isoReq);
            return isoResp.pack();

        } catch(Exception e){
            e.printStackTrace();
            // fallback
            try {
                ISOMsg err = new ISOMsg();
                err.setPackager(new ISO87APackager());
                err.setMTI("0210");
                err.set(39,"96");
                return err.pack();
            } catch(ISOException ex){
                return "ISO-ERROR".getBytes();
            }
        }
    }
}
