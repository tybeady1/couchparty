import com.fairway.monitor.UpdateDownload;
import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
public class UpdateDownloadTest {
  static void check(boolean b) {if(!b)throw new AssertionError();}
  static void reject(String url,int limit)throws Exception {
    try{UpdateDownload.copy(url,new ByteArrayOutputStream(),limit);throw new AssertionError("Accepted invalid response");}catch(IOException expected){}
  }
  public static void main(String[] args)throws Exception {
    KeyStore ks=KeyStore.getInstance("JKS");try(InputStream in=new FileInputStream("build/update-test.jks")){ks.load(in,"testpass".toCharArray());}
    KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,"testpass".toCharArray());
    TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(ks);
    SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),tm.getTrustManagers(),null);
    SSLContext.setDefault(ssl);
    HttpsServer server=HttpsServer.create(new InetSocketAddress("localhost",0),0);server.setHttpsConfigurator(new HttpsConfigurator(ssl));
    server.createContext("/ok",e->{byte[] b="abc".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
    server.createContext("/redirect",e->{e.getResponseHeaders().set("Location","/ok");e.sendResponseHeaders(302,-1);e.close();});
    server.createContext("/downgrade",e->{e.getResponseHeaders().set("Location","http://localhost/file");e.sendResponseHeaders(302,-1);e.close();});
    server.createContext("/loop",e->{e.getResponseHeaders().set("Location","/loop");e.sendResponseHeaders(302,-1);e.close();});
    server.createContext("/chunked",e->{e.sendResponseHeaders(200,0);e.getResponseBody().write(new byte[20]);e.close();});
    server.start();
    try{
      String base="https://localhost:"+server.getAddress().getPort();
      ByteArrayOutputStream out=new ByteArrayOutputStream();String hash=UpdateDownload.copy(base+"/redirect",out,10);
      check("abc".equals(out.toString("UTF-8")));check(hash.equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
      reject(base+"/ok",2);reject(base+"/chunked",5);reject(base+"/downgrade",100);reject(base+"/loop",100);reject(base+"/missing",100);reject("http://localhost/test",100);reject("https://user:pass@localhost/test",100);
      System.out.println("Update download tests passed: HTTPS, redirects, hash, size caps, HTTP errors, credentials, redirect loop and downgrade rejection.");
    }finally{server.stop(0);}
  }
}
