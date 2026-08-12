/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Zugriff auf die Uebersetzungskataloge (gettext-Stil)
 */

package jkcemu.lang;

import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jkcemu.Main;


public class LangUtil
{
  /*
   * "de" bzw. null bedeutet: kein Katalog geladen,
   * tr(...) liefert dann immer den deutschen Quelltext unveraendert.
   */
  public static final String LANG_CODE_DE = "de";

  private static final String RES_PREFIX     = "/lang/";
  private static final String RES_SUFFIX     = ".po";
  private static final String RES_LANGUAGES  = "/lang/languages.txt";
  private static final String CTX_MNEMONIC   = "mnemonic";
  private static final String PROP_COLLECT   = "jkcemu.lang.collect";

  private static volatile String langCode = null;
  private static volatile POFile poFile   = null;

  private static final Object          collectLock = new Object();
  private static Map<String,Boolean>   collectMap  = null;
  private static boolean               collectInit = false;


  public static String[] getAvailableLangCodes()
  {
    List<String> codes = new ArrayList<>();
    InputStream  in    = LangUtil.class.getResourceAsStream(
							RES_LANGUAGES );
    if( in != null ) {
      BufferedReader reader = null;
      try {
	reader = new BufferedReader( new InputStreamReader( in, "UTF-8" ) );
	String line;
	while( (line = reader.readLine()) != null ) {
	  String s = line.trim();
	  if( !s.isEmpty() && !s.startsWith( "#" ) ) {
	    int    pos  = s.indexOf( '=' );
	    String code = (pos >= 0 ? s.substring( 0, pos ) : s).trim();
	    if( !code.isEmpty() ) {
	      codes.add( code );
	    }
	  }
	}
      }
      catch( IOException ex ) {
	Main.printlnErr(
		"Sprachliste " + RES_LANGUAGES
			+ " kann nicht gelesen werden: "
			+ ex.getMessage() );
      }
      finally {
	if( reader != null ) {
	  try {
	    reader.close();
	  }
	  catch( IOException ex ) {}
	} else {
	  try {
	    in.close();
	  }
	  catch( IOException ex ) {}
	}
      }
    }
    return codes.toArray( new String[ codes.size() ] );
  }


  public static String getLangCode()
  {
    String code = langCode;
    return (code != null ? code : LANG_CODE_DE);
  }


  /*
   * Liefert zu einem im Menuetext enthaltenen Mnemonic-Zeichen
   * den dazu passenden Tastencode.
   * Dazu wird im Katalog nach einem Eintrag mit dem Kontext
   * "mnemonic" zum uebergebenen Menuetext gesucht.
   * Ist die Uebersetzung genau ein Zeichen lang,
   * wird daraus der Tastencode ermittelt,
   * anderenfalls wird defaultKey zurueckgeliefert.
   */
  public static int mnemonic( String menuText, int defaultKey )
  {
    int rv = defaultKey;
    if( menuText != null ) {
      String translation = lookup( CTX_MNEMONIC, menuText );
      collect( CTX_MNEMONIC, menuText, translation != null );
      if( (translation != null) && (translation.length() == 1) ) {
	int keyCode = KeyEvent.getExtendedKeyCodeForChar(
						translation.charAt( 0 ) );
	if( keyCode != KeyEvent.VK_UNDEFINED ) {
	  rv = keyCode;
	}
      }
    }
    return rv;
  }


  public static void setLangCode( String code )
  {
    if( (code == null) || code.equals( LANG_CODE_DE ) ) {
      langCode = null;
      poFile   = null;
    } else {
      langCode = code;
      poFile   = loadPOFile( code );
    }
  }


  public static String tr( String text )
  {
    String rv = text;
    if( text != null ) {
      String translation = lookup( null, text );
      collect( null, text, translation != null );
      if( translation != null ) {
	rv = translation;
      }
    }
    return rv;
  }


  /*
   * Uebersetzung eines Textarrays,
   * wobei ein neues Array zurueckgeliefert wird.
   * Die Methode ist fuer Auswahlfelder gedacht,
   * deren Auswahl ueber den Index und nicht ueber den angezeigten
   * Text ausgewertet wird.
   * Bei Auswahlfeldern, die den Text selbst als Wert verwenden,
   * darf sie nicht angewendet werden!
   */
  public static String[] tr( String[] texts )
  {
    String[] rv = texts;
    if( texts != null ) {
      rv = new String[ texts.length ];
      for( int i = 0; i < texts.length; i++ ) {
	rv[ i ] = tr( texts[ i ] );
      }
    }
    return rv;
  }


  public static String tr( String text, Object... args )
  {
    String rv = tr( text );
    if( (rv != null) && (args != null) && (args.length > 0) ) {
      rv = MessageFormat.format( rv, args );
    }
    return rv;
  }


  public static String trCtx( String context, String text )
  {
    String rv = text;
    if( text != null ) {
      String translation = lookup( context, text );
      collect( context, text, translation != null );
      if( translation != null ) {
	rv = translation;
      }
    }
    return rv;
  }


	/* --- private Methoden --- */

  /*
   * Sammeln aller angefragten Texte fuer die QS,
   * gesteuert ueber die System-Property "jkcemu.lang.collect".
   * Ist diese gesetzt, wird ihr Wert als Dateiname interpretiert,
   * in den beim Beenden der JVM alle angefragten Texte
   * zusammen mit dem Ergebnis (Treffer/kein Treffer) geschrieben werden.
   */
  private static void collect( String context, String text, boolean hit )
  {
    if( isCollectEnabled() ) {
      String key = (context != null ? context + '\u0004' + text : text);
      synchronized( collectLock ) {
	Boolean old = collectMap.get( key );
	if( (old == null) || (!old.booleanValue() && hit) ) {
	  collectMap.put( key, Boolean.valueOf( hit ) );
	}
      }
    }
  }


  private static boolean isCollectEnabled()
  {
    if( !collectInit ) {
      synchronized( collectLock ) {
	if( !collectInit ) {
	  final String fileName = System.getProperty( PROP_COLLECT );
	  if( (fileName != null) && !fileName.trim().isEmpty() ) {
	    collectMap = new TreeMap<>();
	    Runtime.getRuntime().addShutdownHook(
		new Thread( "jkcemu.lang.collect" )
			{
			  @Override
			  public void run()
			  {
			    writeCollected( fileName );
			  }
			} );
	  }
	  collectInit = true;
	}
      }
    }
    return collectMap != null;
  }


  private static POFile loadPOFile( String code )
  {
    POFile      rv       = null;
    String      resource = RES_PREFIX + code + RES_SUFFIX;
    InputStream in       = LangUtil.class.getResourceAsStream( resource );
    if( in != null ) {
      try {
	rv = POFile.load( in );
      }
      catch( IOException ex ) {
	Main.printlnErr(
		"Sprachdatei " + resource
			+ " kann nicht geladen werden: "
			+ ex.getMessage() );
      }
      finally {
	try {
	  in.close();
	}
	catch( IOException ex ) {}
      }
    } else {
      Main.printlnErr( "Sprachdatei " + resource + " nicht gefunden" );
    }
    return rv;
  }


  private static String lookup( String context, String text )
  {
    POFile pf = poFile;
    return (pf != null ? pf.getTranslation( context, text ) : null);
  }


  private static void writeCollected( String fileName )
  {
    Map<String,Boolean> map = collectMap;
    if( map != null ) {
      PrintWriter out = null;
      try {
	out = new PrintWriter(
		new OutputStreamWriter(
			new FileOutputStream( fileName ), "UTF-8" ) );
	for( Map.Entry<String,Boolean> e : map.entrySet() ) {
	  out.print( e.getValue().booleanValue() ? "HIT\t" : "MISS\t" );
	  out.println( e.getKey() );
	}
      }
      catch( IOException ex ) {
	System.err.println(
		"Sammeldatei " + fileName
			+ " kann nicht geschrieben werden: "
			+ ex.getMessage() );
      }
      finally {
	if( out != null ) {
	  out.close();
	}
      }
    }
  }


	/* --- Konstruktor --- */

  private LangUtil()
  {
    // nicht instanziierbar
  }
}
