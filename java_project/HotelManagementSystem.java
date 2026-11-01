import com.sun.net.httpserver.*;
import java.awt.Desktop;
import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;

/** Single-file Hotel Management System; requires only Java 11+. Run: java HotelManagementSystem.java */
public class HotelManagementSystem {
    static final Path FILE = Paths.get(System.getenv().getOrDefault("HOTEL_DATA_FILE", "data/hotel-data.log")).toAbsolutePath();
    static final ZoneId ZONE = ZoneId.of(System.getenv().getOrDefault("HOTEL_TIMEZONE", "Asia/Kolkata"));
    static final List<String> TYPES = Arrays.asList("SINGLE", "DOUBLE", "DELUXE", "SUITE");

    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.net.usePlainSocketImpl", "true");
        System.setProperty("java.net.preferIPv4Stack", "true");
        Path unixSocketTemp = Paths.get(System.getProperty("user.home"), ".hotel-sockets");
        Files.createDirectories(unixSocketTemp);
        System.setProperty("jdk.net.unixdomain.tmpdir", unixSocketTemp.toString());
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", args.length > 0 ? args[0] : "8080"));
        Hotel hotel = new Hotel(FILE);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", x -> handle(x, hotel));
        server.setExecutor(Executors.newFixedThreadPool(8)); server.start();
        String url = "http://127.0.0.1:" + port;
        System.out.println("Hotel Management System: " + url + "\nData file: " + FILE);
        try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url)); } catch (Exception ignored) { }
    }

    static void handle(HttpExchange x, Hotel h) throws IOException {
        try {
            String p=x.getRequestURI().getPath(), m=x.getRequestMethod();
            if (m.equals("GET") && p.equals("/")) send(x,200,"text/html; charset=utf-8",PAGE);
            else if (m.equals("GET") && p.equals("/api/rooms")) json(x,200,h.rooms());
            else if (m.equals("POST") && p.equals("/api/rooms")) json(x,201,h.addRoom(obj(read(x))));
            else if (m.equals("GET") && p.equals("/api/check-ins")) json(x,200,h.stays());
            else if (m.equals("POST") && p.equals("/api/check-ins")) json(x,201,h.checkIn(obj(read(x))));
            else if (m.equals("GET") && p.equals("/api/availability")) {
                Map<String,String> q=query(x.getRequestURI().getRawQuery());
                json(x,200,h.availability(q.get("roomType"),q.get("checkInDate"),q.get("checkOutDate")));
            } else json(x,404,kv("message","Endpoint not found"));
        } catch (Problem e) { json(x,e.status,kv("message",e.getMessage())); }
        catch (Exception e) { e.printStackTrace(); json(x,500,kv("message","Request could not be completed")); }
        finally { x.close(); }
    }
    static String read(HttpExchange x) throws IOException { try(InputStream in=x.getRequestBody()){return new String(in.readAllBytes(),StandardCharsets.UTF_8);} }
    static void json(HttpExchange x,int s,Object v)throws IOException{send(x,s,"application/json; charset=utf-8",J.write(v));}
    static void send(HttpExchange x,int s,String type,String body)throws IOException{
        byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",type);x.getResponseHeaders().set("Cache-Control","no-store");
        x.sendResponseHeaders(s,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}
    }
    static Map<String,String> query(String q){Map<String,String> r=new HashMap<>();if(q!=null)for(String part:q.split("&")){String[] a=part.split("=",2);if(a.length==2)r.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),URLDecoder.decode(a[1],StandardCharsets.UTF_8));}return r;}
    static Map<String,Object> kv(Object... a){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<a.length;i+=2)m.put((String)a[i],a[i+1]);return m;}
    @SuppressWarnings("unchecked") static Map<String,Object> obj(String s){Object v=J.parse(s);if(!(v instanceof Map))throw new Problem(400,"Expected a JSON object");return (Map<String,Object>)v;}

    static final String PAGE = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>Hotel Management</title>"+
      "<style>body{font:16px system-ui;max-width:950px;margin:30px auto;padding:0 16px;background:#f3f6fa;color:#1d2939}h1{color:#174a70}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px}.card{background:white;border:1px solid #ddd;border-radius:10px;padding:18px;margin:14px 0}label{display:block;margin:10px 0 4px}input,select{box-sizing:border-box;width:100%;font:inherit;padding:8px;border:1px solid #bbb;border-radius:6px}button{margin-top:14px;padding:10px 14px;background:#1671b8;color:white;border:0;border-radius:6px;cursor:pointer}table{border-collapse:collapse;width:100%}td,th{text-align:left;padding:8px;border-bottom:1px solid #ddd}#message{white-space:pre-wrap}</style></head><body>"+
      "<h1>Hotel Management System</h1><p>Room and guest records start empty. Add room inventory before checking in guests.</p><div class='grid'>"+
      "<section class='card'><h2>Add room</h2><form id='rf'><label>Room number</label><input name='number' required maxlength='20'><label>Room type</label><select name='roomType'><option>SINGLE</option><option>DOUBLE</option><option>DELUXE</option><option>SUITE</option></select><label>Price per night</label><input name='pricePerNight' type='number' min='0' step='.01' value='0' required><button>Add room</button></form></section>"+
      "<section class='card'><h2>Guest check-in</h2><form id='cf'><label>Guest name</label><input name='guestName' required maxlength='150'><label>Contact phone</label><input name='phone' required maxlength='40'><label>Email (optional)</label><input name='email' type='email' maxlength='150'><label>Room type</label><select name='roomType'><option>SINGLE</option><option>DOUBLE</option><option>DELUXE</option><option>SUITE</option></select><label>Check-in date</label><input name='checkInDate' type='date' required><label>Check-out date</label><input name='checkOutDate' type='date' required><div id='avail'></div><button>Check in and assign room</button></form></section></div>"+
      "<div id='message'></div><section class='card'><h2>Room inventory</h2><div id='rooms'></div></section><section class='card'><h2>Check-ins</h2><div id='stays'></div></section><script>const $=s=>document.querySelector(s),data=f=>Object.fromEntries(new FormData(f));"+
      "async function api(u,o){let r=await fetch(u,o),d=await r.json();if(!r.ok)throw Error(d.message||'Request failed');return d}async function refresh(){let rs=await api('/api/rooms'),ss=await api('/api/check-ins');$('#rooms').innerHTML=rs.length?'<table><tr><th>Room</th><th>Type</th><th>Price</th></tr>'+rs.map(r=>`<tr><td>${r.number}</td><td>${r.roomType}</td><td>${r.pricePerNight}</td></tr>`).join('')+'</table>':'No rooms added.';$('#stays').innerHTML=ss.length?'<table><tr><th>Guest</th><th>Contact</th><th>Room</th><th>Dates</th></tr>'+ss.slice().reverse().map(s=>`<tr><td>${s.guestName}</td><td>${s.phone}</td><td>${s.roomNumber}</td><td>${s.checkInDate} – ${s.checkOutDate}</td></tr>`).join('')+'</table>':'No check-ins yet.';availability()}"+
      "async function availability(){let f=data($('#cf'));if(!f.checkInDate)return;try{let a=await api('/api/availability?roomType='+f.roomType+'&checkInDate='+f.checkInDate+'&checkOutDate='+$('#cf [name=checkOutDate]').value);$('#avail').textContent=a.availableRooms+' matching room(s) available'}catch(e){$('#avail').textContent=e.message}}"+
      "$('#rf').onsubmit=async e=>{e.preventDefault();try{await api('/api/rooms',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data(e.target))});e.target.reset();$('#message').textContent='Room added.';refresh()}catch(x){$('#message').textContent=x.message}};"+
      "$('#cf').onsubmit=async e=>{e.preventDefault();try{let s=await api('/api/check-ins',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data(e.target))});$('#message').textContent='Checked in '+s.guestName+' to room '+s.roomNumber+'.';e.target.reset();refresh()}catch(x){$('#message').textContent=x.message}};$('#cf').onchange=availability;$('#cf').oninput=availability;let today=new Date().toLocaleDateString('en-CA');$('#cf [name=checkInDate]').value=today;$('#cf [name=checkOutDate]').value=new Date(Date.now()+86400000).toLocaleDateString('en-CA');refresh().catch(e=>$('#message').textContent=e.message);</script></body></html>";

    static final class Hotel {
        final Path file; final List<Map<String,Object>> rooms=new ArrayList<>(), stays=new ArrayList<>(); long roomId=1,stayId=1;
        Hotel(Path file)throws IOException{this.file=file;Path d=file.getParent();if(d!=null)Files.createDirectories(d);if(Files.exists(file)){int n=0;for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){n++;if(line.trim().isEmpty())continue;try{replay(obj(line));}catch(RuntimeException e){System.err.println("Skipping invalid data record at line "+n);}}}}
        synchronized List<Map<String,Object>> rooms(){return copies(rooms);} synchronized List<Map<String,Object>> stays(){return copies(stays);}
        synchronized Map<String,Object> addRoom(Map<String,Object> in)throws IOException{
            String no=req(in,"number"),type=req(in,"roomType").toUpperCase();if(!TYPES.contains(type))throw new Problem(400,"Choose a valid room type");
            BigDecimal price;try{price=new BigDecimal(String.valueOf(in.getOrDefault("pricePerNight","0")));}catch(Exception e){throw new Problem(400,"Price must be a number");}if(price.signum()<0)throw new Problem(400,"Price cannot be negative");
            for(Map<String,Object> r:rooms)if(no.equalsIgnoreCase(String.valueOf(r.get("number"))))throw new Problem(409,"That room number already exists");
            Map<String,Object> r=kv("id",roomId,"number",no,"roomType",type,"pricePerNight",price);append(kv("kind","room","data",r));rooms.add(r);roomId++;return new LinkedHashMap<>(r);
        }
        synchronized Map<String,Object> checkIn(Map<String,Object> in)throws IOException{
            String name=req(in,"guestName"),phone=req(in,"phone"),email=String.valueOf(in.getOrDefault("email","")).trim(),type=req(in,"roomType").toUpperCase();if(!TYPES.contains(type))throw new Problem(400,"Choose a valid room type");
            if(!email.isEmpty()&&!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new Problem(400,"Enter a valid email address");
            LocalDate start=date(req(in,"checkInDate")),end=date(req(in,"checkOutDate"));if(start.isBefore(LocalDate.now(ZONE)))throw new Problem(400,"Check-in date cannot be in the past");if(!end.isAfter(start))throw new Problem(400,"Check-out date must be after check-in date");
            Map<String,Object> room=null;for(Map<String,Object> r:rooms)if(type.equals(r.get("roomType"))&&free(r,start,end)){room=r;break;}if(room==null)throw new Problem(409,"No vacant "+type+" room is available for those dates");
            Map<String,Object> stay=kv("id",stayId,"guestName",name,"phone",phone,"email",email,"roomId",room.get("id"),"roomNumber",room.get("number"),"roomType",type,"checkInDate",start.toString(),"checkOutDate",end.toString(),"status","CHECKED_IN");
            append(kv("kind","check-in","data",stay));stays.add(stay);stayId++;return new LinkedHashMap<>(stay);
        }
        synchronized Map<String,Object> availability(String type,String start,String end){if(type==null||type.trim().isEmpty())throw new Problem(400,"roomType is required");LocalDate a=date(start),b=end==null||end.isEmpty()?a.plusDays(1):date(end);if(!b.isAfter(a))throw new Problem(400,"Check-out date must be after check-in date");String t=type.toUpperCase();long count=rooms.stream().filter(r->t.equals(r.get("roomType"))&&free(r,a,b)).count();return kv("roomType",t,"checkInDate",a.toString(),"checkOutDate",b.toString(),"availableRooms",count);}
        boolean free(Map<String,Object> room,LocalDate a,LocalDate b){long id=((Number)room.get("id")).longValue();for(Map<String,Object>s:stays)if(((Number)s.get("roomId")).longValue()==id&&a.isBefore(LocalDate.parse(String.valueOf(s.get("checkOutDate"))))&&b.isAfter(LocalDate.parse(String.valueOf(s.get("checkInDate")))))return false;return true;}
        void append(Map<String,Object> record)throws IOException{Files.writeString(file,J.write(record)+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        void replay(Map<String,Object> record){String kind=String.valueOf(record.get("kind"));if(record.get("data") instanceof Map){Map<String,Object>d=cast(record.get("data"));if(kind.equals("room")){rooms.add(d);roomId=Math.max(roomId,((Number)d.get("id")).longValue()+1);}else if(kind.equals("check-in")){stays.add(d);stayId=Math.max(stayId,((Number)d.get("id")).longValue()+1);}}}
    }
    @SuppressWarnings("unchecked") static Map<String,Object> cast(Object o){return (Map<String,Object>)o;}
    static List<Map<String,Object>> copies(List<Map<String,Object>> a){List<Map<String,Object>> b=new ArrayList<>();for(Map<String,Object>m:a)b.add(new LinkedHashMap<>(m));return b;}
    static String req(Map<String,Object>m,String k){String s=m.get(k)==null?"":String.valueOf(m.get(k)).trim();if(s.isEmpty())throw new Problem(400,k+" is required");return s;}
    static LocalDate date(String s){try{return LocalDate.parse(s);}catch(Exception e){throw new Problem(400,"Date must use YYYY-MM-DD format");}}
    static final class Problem extends RuntimeException{final int status;Problem(int s,String m){super(m);status=s;}}

    // Small JSON reader/writer keeps the data file portable and dependency-free.
    static final class J {
        static String write(Object v){if(v==null)return "null";if(v instanceof String)return quote((String)v);if(v instanceof Number||v instanceof Boolean)return v.toString();if(v instanceof Map){StringBuilder b=new StringBuilder("{");boolean f=true;for(Object z:((Map<?,?>)v).entrySet()){Map.Entry<?,?>e=(Map.Entry<?,?>)z;if(!f)b.append(',');f=false;b.append(quote(String.valueOf(e.getKey()))).append(':').append(write(e.getValue()));}return b.append('}').toString();}if(v instanceof Iterable){StringBuilder b=new StringBuilder("[");boolean f=true;for(Object z:(Iterable<?>)v){if(!f)b.append(',');f=false;b.append(write(z));}return b.append(']').toString();}return quote(v.toString());}
        static String quote(String s){StringBuilder b=new StringBuilder("\"");for(char c:s.toCharArray())switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}return b.append('"').toString();}
        static Object parse(String s){return new P(s).value();}
        static final class P{final String s;int i;P(String s){this.s=s;}void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}boolean take(char c){ws();if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}void need(char c){if(!take(c))bad();}void bad(){throw new Problem(400,"Malformed JSON");}
            Object value(){ws();if(i>=s.length())bad();char c=s.charAt(i);if(c=='{')return object();if(c=='[')return array();if(c=='"')return string();if(s.startsWith("true",i)){i+=4;return true;}if(s.startsWith("false",i)){i+=5;return false;}if(s.startsWith("null",i)){i+=4;return null;}int a=i;if(c=='-')i++;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;if(i<s.length()&&s.charAt(i)=='.'){i++;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;}if(a==i)bad();try{return new BigDecimal(s.substring(a,i));}catch(Exception e){bad();return null;}}
            Map<String,Object> object(){Map<String,Object>m=new LinkedHashMap<>();i++;if(take('}'))return m;do{ws();if(i>=s.length()||s.charAt(i)!='"')bad();String k=string();need(':');m.put(k,value());if(take('}'))return m;need(',');}while(true);}
            List<Object> array(){List<Object>a=new ArrayList<>();i++;if(take(']'))return a;do{a.add(value());if(take(']'))return a;need(',');}while(true);}
            String string(){need('"');StringBuilder b=new StringBuilder();while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c=='\\'){if(i>=s.length())bad();char e=s.charAt(i++);switch(e){case '"':case '\\':case '/':b.append(e);break;case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;case 'b':b.append('\b');break;case 'f':b.append('\f');break;case 'u':if(i+4>s.length())bad();b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;break;default:bad();}}else b.append(c);}bad();return null;}
        }
    }
}
