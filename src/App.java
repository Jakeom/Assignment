
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Map.Entry;
import com.google.gson.*;

public class App {

    static String BaseUrl = "https://www.bankofcanada.ca/valet";

    static List<String> nameList = new ArrayList<>();
    static HashMap<String, String> csvCheckHashMap = new HashMap<>();

    static String strDate = "";
    static String endDate = "";

    // Check Date Format
    public static boolean checkDate(String checkDate) {
        try {
            SimpleDateFormat dateFormatParser = new SimpleDateFormat("yyyy-MM-dd"); 
            dateFormatParser.setLenient(false); 
            dateFormatParser.parse(checkDate); 
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Parameters Validation 
    public static String validation(String[] args){

        String errStr = "";

        //If no parameters.
        if(args.length > 0){

            // Optional challenge #3 : If arg[0] is "group", collect all series for the series group FX_RATES_DAILY
            if(args.length == 1 && !"group".equals(args[0])){
                errStr = "Input one Parameter is allowed just 'group'.";
            }else if(args.length >= 2){
                if(!checkDate(args[0]) ||!checkDate(args[1])){
                    errStr = "Parameter 1 and 2 Must be Date Format [yyyy-mm-dd].";
                }                
            }

            // Check Parameter count
            if(args.length > 6){
                errStr = "Must be 6 parameters; parameter 1 and 2 = yyyy-mm-dd,  3 to 6 = Series Name.";
            }

            // Check Parameter Max Value
            for(int i = 2; i < args.length; i ++){
                if(args[i].length() > 20){
                    errStr = "Max Length of Name is 20";
                }
            }
        }

        return errStr;
    }

    static int total = 0;

    public static void main(String[] args) throws Exception {
        
        System.out.println("Start time:"+new Date());
        // Set past week Date Mon - Fri 
        LocalDate today = LocalDate.now().minus(1, ChronoUnit.WEEKS);
        strDate = today.with( TemporalAdjusters.previous( DayOfWeek.MONDAY ) ).toString();
        endDate = today.with( TemporalAdjusters.previous( DayOfWeek.FRIDAY ) ).toString();

        //If no parameters.
        if(args.length == 0){
            // Set series name; FXCADUSD and FXAUDCAD. 
            nameList.add("FXCADUSD");
            nameList.add("FXAUDCAD");
        }else{
            // Parameters Validation 
            String errStr = validation(args);
            if(errStr.length() > 0){
                System.out.println(errStr); // Error message printing
                return;
            }else if(args.length == 1  && "group".equals(args[0])){
                getGroupApiData(); // collect all series
            }else{
                // Set Parameters
                strDate = args[0]; 
                endDate = args[1];
                for(int i = 2; i < args.length; i ++){
                    nameList.add(args[i]);
                }
            }
        }        

        /** Optional challenge #2: If the program is run multiple times, data already existing in the file will
            be skipped, and new data will be appended to the file while maintaining the correct sorting.
        */
        // read CSV file to HashMap
        List<Series> seriesList = new ArrayList<>();

        readCSV(seriesList); // it will be make HashMap for CSV File 


        // Get data for supported series made available by the API For Parameters.
        for(String sName : nameList){
            getSeriesApiData(sName,strDate,endDate,seriesList);
        }


        // option2 : already exist data No append
        

        // Group the data by Series Name and sorted by Date.
        Collections.sort(seriesList, Comparator.comparing(Series::getName).reversed()
            .thenComparing(Series::getDate).reversed()
            );

        /**  Optional challenge #1: The program will calculate a new column named “Change”, which
             equals the percent change between current and previous entries.
        */
        for(int i = 0; i < seriesList.size(); i ++){
            Series s = seriesList.get(i);
            Series nextS = null;
            if(seriesList.size() > i+1){
                nextS = seriesList.get(i+1);
            }
            if(nextS != null && nextS.getName().equals(s.getName())) {
                Float chnage =  ((Float.parseFloat(s.getValue()))/(Float.parseFloat(nextS.getValue()))-1)*100;
                s.setChange(String.format("%.4f", chnage)+"%");
            }
        }

        // Write the data in a CSV file with the following headers: Date, Series Name, Label,Description, Value
        createCSV(seriesList);

        System.out.println("End time:"+new Date());
    }

    // Series data get API.
    public static void getSeriesApiData(String sname,String strDate, String EndDate, List<Series> seriesList) throws Exception{
        JsonObject data = getApiData(BaseUrl+"/observations/"+sname,"start_date="+strDate+"&end_date="+EndDate);
        if(data != null){
            String name = "";
            String lable = "";
            String description = "";
            if(data.has("seriesDetail")){
                JsonObject tmp1 = data.get("seriesDetail").getAsJsonObject();
                for (Entry<String, JsonElement> e : tmp1.entrySet()) {
                    name = e.getKey();
                    JsonObject tmp2 = tmp1.get(name).getAsJsonObject();
                    lable = tmp2.get("label").getAsString();
                    description = tmp2.get("description").getAsString();
                    break;
                }
            }

            if(data.has("observations")){
                JsonArray tmp1 = data.get("observations").getAsJsonArray();
                total += tmp1.size();
                for (JsonElement e : tmp1) {
                    JsonObject o = e.getAsJsonObject();
                    String date = o.get("d").getAsString();
                    JsonObject v = o.get(name).getAsJsonObject();
                    String value = v.get("v").getAsString();

                    if(csvCheckHashMap.get(date+name)== null){
                        seriesList.add(new Series(date,name, lable, description,value,""));                    
                    }
                }
            }
        }
    }

    // Group Data get Api.
    public static void getGroupApiData() throws Exception{
        JsonObject data = getApiData(BaseUrl+"/groups/FX_RATES_DAILY","");
        if(data != null){
            if(data.has("groupDetails")){
                JsonObject tmp1 = data.get("groupDetails").getAsJsonObject();
                JsonObject tmp2 = tmp1.get("groupSeries").getAsJsonObject();
                for (Entry<String, JsonElement> e : tmp2.entrySet()) {
                    nameList.add(e.getKey());
                }
            }
        }
    }

    // Previous CSV Read
    public static HashMap<String,Object> readCSV(List<Series> seriesList){
        try {
            File myObj = new File("result.csv");
            if(myObj.exists()){
                Scanner myReader = new Scanner(myObj);
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    String pv[] = data.split(",");
                    if(!"Date".equals(pv[0])){
                        csvCheckHashMap.put(pv[0]+pv[1], "");
                        seriesList.add(new Series(pv[0],pv[1], pv[2], pv[3], pv[4],""));
                    }
                }
                myReader.close();
            }
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          }
        return null;
    }

    // Write the data in a CSV file with the following headers: Date, Series Name, Label,Description, Value
    public static void createCSV(List<Series> datas){
        // Create csv file
        File csv = new File("result.csv");
        BufferedWriter bw = null;
        try{
            bw = new BufferedWriter(new FileWriter(csv)); // file re write
            // making headers: 
            bw.write("Date,Series Name,Label,Description,Value,Change");
            bw.newLine();

            // Write csv file
            for(Series data : datas){
                bw.write(data.csvString());
                bw.newLine();
            }
        }catch(Exception e){
            e.printStackTrace();
        } finally {
            // file close
            if(bw != null){
                try {
                    bw.flush();
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Get Api data [JsonObject]
    public static JsonObject getApiData(String url, String params) throws Exception {
        String result = null;
    
        URL obj = new URL(url + "?" + params);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        //System.out.println(obj);
        con.setRequestMethod("GET");
    
        int responseCode = con.getResponseCode();

        // return to null if it has error.
        if(responseCode != 200){
            return null;
        }
        
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
    
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
    
        result = response.toString();

        
        JsonObject convertedObject = new Gson().fromJson(result, JsonObject.class);
        
        return convertedObject;
    }

    // Series Bean
    private static class Series {

        private String date;
        private String name;
        private String label;
        private String description;
        private String value;
        private String change;

        public String getDate() {
            return this.date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return this.label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDescription() {
            return this.description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getChange() {
            return this.change;
        }

        public void setChange(String change) {
            this.change = change;
        }

        public String getCheckKey(){
            return this.date + this.name;
        }

        public Series(String date, String name, String label, String description,String value, String change) {
            this.date = date;
            this.name = name;
            this.label = label;
            this.description = description;
            this.value = value;
            this.change = change;
        }

       public String csvString(){
        return this.date + ","+ this.name + ","+ this.label+ ","+ this.description+ ","+ this.value+ ","+ this.change;
       }
    }
}
