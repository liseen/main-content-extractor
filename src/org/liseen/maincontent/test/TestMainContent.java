package org.liseen.maincontent.test;


import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class TestMainContent
{
    public static void testNew(String rawHtmlFile) {
        try {
            String url = "";
            String rawHtml = "";
            FileInputStream finstream = new FileInputStream(rawHtmlFile);
            DataInputStream in = new DataInputStream(finstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;
            while ((strLine = br.readLine()) != null) {
                if (0 == url.length()) {
                    url = strLine;
                } else {
                    rawHtml += strLine;
                }
            }
            in.close();
            
            org.liseen.maincontent.extract.Extractor extractor = org.liseen.maincontent.extract.Extractor.getInstance();
            org.liseen.maincontent.extract.Page page = null;
            
            long before = System.currentTimeMillis();
            for (int i = 0; i < 1; i++)
            	page = extractor.extract(url, rawHtml);
            long after = System.currentTimeMillis();
            
            JSONObject jo = new JSONObject();
            jo.put("url", url);
            jo.put("elasped_time", after - before);
            if (page != null) {
	           
	            jo.put("title", page.title);
	            jo.put("content", page.mainContent);
	            jo.put("author", page.author);
	            jo.put("pub_time", page.pubTime);
            }
            System.out.print(jo.toString(4));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String args[]) {

        if (args.length != 1) {
            System.err.println("The arguments of main content extracotr is not right");
            return;
        }

        testNew(args[0]);
    }
}
