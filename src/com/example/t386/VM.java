package com.example.t386;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class VM {
    public String name = "新虚拟机";
    public int mem = 8, gen = 4, w = 720, h = 480;
    public int boot = 0;                 // 0=软盘 1=硬盘 2=光驱
    public String fda = "", hda = "", cda = "";
    public boolean builtin = false;   /* 内置引导盘：不可改不可删 */

    public static File file(Context c) { return new File(c.getFilesDir(), "vms.json"); }

    public static List<VM> load(Context c) {
        List<VM> out = new ArrayList<VM>();
        try {
            File f = file(c);
            if (!f.exists()) return out;
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            in.read(b); in.close();
            JSONArray a = new JSONArray(new String(b, "UTF-8"));
            for (int i = 0; i < a.length(); i++) out.add(from(a.getJSONObject(i)));
        } catch (Throwable t) { }
        return out;
    }

    public static void save(Context c, List<VM> list) {
        try {
            JSONArray a = new JSONArray();
            for (int i = 0; i < list.size(); i++) a.put(list.get(i).to());
            FileOutputStream o = new FileOutputStream(file(c));
            o.write(a.toString().getBytes("UTF-8")); o.close();
        } catch (Throwable t) { }
    }

    static VM from(JSONObject o) {
        VM v = new VM();
        v.name = o.optString("name", "虚拟机");
        v.mem  = o.optInt("mem", 8);
        v.gen  = o.optInt("gen", 4);
        v.w    = o.optInt("w", 720);
        v.h    = o.optInt("h", 480);
        v.boot = o.optInt("boot", 0);
        v.fda  = o.optString("fda", "");
        v.hda  = o.optString("hda", "");
        v.cda  = o.optString("cda", "");
        v.builtin = o.optBoolean("builtin", false) || v.name.startsWith("\u5185\u7f6e");
        return v;
    }

    JSONObject to() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name); o.put("mem", mem); o.put("gen", gen);
            o.put("w", w); o.put("h", h); o.put("boot", boot);
            o.put("fda", fda); o.put("hda", hda); o.put("cda", cda);
            o.put("builtin", builtin);
        } catch (Throwable t) { }
        return o;
    }

    public String bootName() {
        return boot == 0 ? "软盘" : (boot == 1 ? "硬盘" : "光驱");
    }

    public String summary() {
        return mem + "M · " + (gen == 3 ? "386" : gen == 4 ? "486" : "586")
             + " · " + w + "×" + h + " · 首引导:" + bootName();
    }
}
