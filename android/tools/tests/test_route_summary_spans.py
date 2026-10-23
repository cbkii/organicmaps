"""Execute the production rail span sanitiser; no new Android test dependency."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class RouteSummarySpansTest(unittest.TestCase):
    def test_mobile_colours_are_replaced_without_mutating_the_source(self):
        if not shutil.which('java'):
            self.skipTest('JDK unavailable')
        files = {
            'androidx/annotation/NonNull.java': 'package androidx.annotation;public @interface NonNull {}',
            'android/graphics/Color.java': 'package android.graphics;public class Color{public static int WHITE=-1;}',
            'android/text/Spannable.java': 'package android.text;public interface Spannable {int SPAN_EXCLUSIVE_EXCLUSIVE=33;}',
            'android/text/style/ForegroundColorSpan.java': 'package android.text.style;public class ForegroundColorSpan{public final int colour;public ForegroundColorSpan(int c){colour=c;}}',
            'android/text/SpannableStringBuilder.java': '''package android.text;import java.util.*;
public class SpannableStringBuilder implements CharSequence {String text;public List<Object> spans=new ArrayList<>();
public SpannableStringBuilder(CharSequence s){text=s.toString();if(s instanceof SpannableStringBuilder b)spans.addAll(b.spans);}
public int length(){return text.length();}public char charAt(int i){return text.charAt(i);}public CharSequence subSequence(int a,int b){return text.substring(a,b);}public String toString(){return text;}
@SuppressWarnings("unchecked")public <T>T[] getSpans(int a,int b,Class<T> k){return spans.stream().filter(k::isInstance).toArray(n->(T[])java.lang.reflect.Array.newInstance(k,n));}
public void removeSpan(Object o){spans.remove(o);}public void setSpan(Object o,int a,int b,int flags){if(a!=0||b!=length()||flags!=33)throw new AssertionError("incorrect range");spans.add(o);}}''',
            'Harness.java': '''import android.text.*;import android.text.style.*;import app.organicmaps.incar.InCarRouteSummary;
class Harness {public static void main(String[] args){for(String text:new String[]{"2 min • 1.4 km","123 hours • 99999 km","٢ دقيقة • ١٫٤ كم",""}){
var source=new SpannableStringBuilder(text);Object formatting=new Object();source.spans.add(formatting);source.spans.add(new ForegroundColorSpan(0xff000000));source.spans.add(new ForegroundColorSpan(0xff444444));
var result=(SpannableStringBuilder)InCarRouteSummary.onBlackRail(source);var colours=result.getSpans(0,result.length(),ForegroundColorSpan.class);
if(!text.equals(result.toString())||source.spans.size()!=3||!result.spans.contains(formatting))throw new AssertionError("format/source changed");
if(colours.length!=(text.isEmpty()?0:1)||!text.isEmpty()&&colours[0].colour!=-1)throw new AssertionError("dark colour survived black rail");
}System.out.println("PASS production route-summary span copy / colours / formatting / RTL / empty");}}''',
        }
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            for name, content in files.items():
                p = work/name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(content)
            production = ROOT/'android/app/src/main/java/app/organicmaps/incar/InCarRouteSummary.java'
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(work),
                            *map(str, work.rglob('*.java')), str(production)], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(work), 'Harness'], check=True, timeout=10)
