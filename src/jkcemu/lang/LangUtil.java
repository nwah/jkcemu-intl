/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Zugriff auf die Sprachschluessel (ResourceBundle-basiert)
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
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.regex.Pattern;
import jkcemu.Main;


public class LangUtil
{
  /*
   * "de" ist der Sprachschluessel, der verwendet wird,
   * wenn noch nie setLangCode(...) aufgerufen wurde.
   * Im Unterschied zum alten gettext-basierten System wird auch
   * fuer "de" ein echtes ResourceBundle geladen
   * (lang/jkcemu_de.properties), da an den Aufrufstellen inzwischen
   * durchgaengig symbolische Schluessel uebergeben werden --
   * auch fuer den deutschen Text.
   */
  public static final String LANG_CODE_DE = "de";

  private static final String  BUNDLE_BASE_NAME = "lang.jkcemu";
  private static final String  RES_LANGUAGES    = "/lang/languages.txt";
  private static final String  MNEMONIC_SUFFIX  = ".mnemonic";
  private static final String  PROP_COLLECT     = "jkcemu.lang.collect";
  private static final Pattern KEY_PATTERN      = Pattern.compile(
					"[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+" );

  private static volatile String         langCode    = null;
  private static volatile ResourceBundle bundle      = null;
  private static volatile boolean        initialized = false;

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


  public static void setLangCode( String code )
  {
    String c = ( (code == null) || code.equals( LANG_CODE_DE ) )
			? LANG_CODE_DE
			: code;
    langCode    = c;
    initialized = true;

    /*
     * getNoFallbackControl unterdrueckt lediglich den Rueckfall auf
     * das Bundle der JVM-Standard-Locale, falls fuer die angeforderte
     * Locale ueberhaupt nichts gefunden wird.
     * Innerhalb der Kandidatenkette der angeforderten Locale selbst
     * bleibt der Rueckfall auf das Basis-Bundle erhalten.
     * Damit liefert "de" das deutsche Bundle jkcemu_de.properties,
     * und jeder andere bzw. unbekannte Schluessel liefert ueber den
     * Rueckfall innerhalb dieser Kette das englische Basis-Bundle
     * jkcemu.properties.
     */
    try {
      bundle = ResourceBundle.getBundle(
			BUNDLE_BASE_NAME,
			Locale.forLanguageTag( c ),
			LangUtil.class.getClassLoader(),
			ResourceBundle.Control.getNoFallbackControl(
				ResourceBundle.Control.FORMAT_PROPERTIES ) );
    }
    catch( MissingResourceException ex ) {
      Main.printlnErr(
		"Sprachschluessel-Katalog fuer \'" + c
			+ "\' kann nicht geladen werden: "
			+ ex.getMessage() );
      bundle = null;
    }

    /*
     * Die von Swing selbst mitgebrachten Texte,
     * z.B. die Beschriftungen der Knoepfe in JOptionPane
     * und die Texte im JFileChooser,
     * werden ueber die Kategorie DISPLAY der Standard-Landeseinstellung
     * ausgewaehlt.
     * Diese wird deshalb mitgefuehrt, damit nicht in einem
     * englischsprachigen Dialog ein Knopf "Abbrechen" erscheint.
     * Die Kategorie FORMAT bleibt unveraendert,
     * damit z.B. Datums- und Zahlenformate
     * weiterhin denen des Betriebssystems entsprechen.
     */
    try {
      Locale.setDefault(
			Locale.Category.DISPLAY,
			Locale.forLanguageTag( c ) );
    }
    catch( Exception ex ) {}
  }


  public static String getText( String keyOrText )
  {
    if( keyOrText == null ) {
      return null;
    }
    ensureInitialized();

    String         rv  = keyOrText;
    boolean        hit = false;
    ResourceBundle b   = bundle;
    if( b != null ) {
      try {
	rv  = b.getString( keyOrText );
	hit = true;
      }
      catch( MissingResourceException ex ) {
	// Schluessel nicht im Katalog enthalten -> unten unveraendert liefern
      }
    }

    /*
     * Diese Methode muss tolerant sein:
     * An generischen Text-Senken dieser Anwendung
     * (z.B. GUIFactory, BaseDlg, OptionDlg, FileFormat
     * und aehnliche Hilfsklassen) wird entweder ein symbolischer
     * Schluessel (aus einer statischen Schluessel-Konstante)
     * oder bereits uebersetzter bzw. zur Laufzeit dynamisch
     * zusammengesetzter Text uebergeben, der gar kein Schluessel ist.
     * Deshalb wird ein nicht erkannter Schluessel nicht als Fehler
     * behandelt, sondern unveraendert zurueckgeliefert,
     * anstatt eine Ausnahme auszuloesen oder den Text zu verstuemmeln.
     */
    collect( keyOrText, hit );
    return rv;
  }


  /*
   * Uebersetzung eines Textes mit Platzhaltern
   *
   * Zahlenargumente werden vorher in Zeichenketten umgewandelt,
   * damit MessageFormat sie nicht entsprechend der Landeseinstellung
   * formatiert.
   * Anderenfalls wuerde z.B. aus 1024 die Ausgabe "1.024" werden,
   * was bei den hier auszugebenden technischen Werten
   * (Adressen, Anzahlen, Byte- und Sektorgroessen) falsch waere.
   */
  public static String getText( String key, Object... args )
  {
    String rv = getText( key );
    if( (rv != null) && (args != null) && (args.length > 0) ) {
      Object[] fmtArgs = new Object[ args.length ];
      for( int i = 0; i < args.length; i++ ) {
	Object arg = args[ i ];
	if( arg instanceof Number ) {
	  arg = arg.toString();
	}
	fmtArgs[ i ] = arg;
      }
      rv = MessageFormat.format( rv, fmtArgs );
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
  public static String[] getTexts( String[] texts )
  {
    String[] rv = texts;
    if( texts != null ) {
      rv = new String[ texts.length ];
      for( int i = 0; i < texts.length; i++ ) {
	rv[ i ] = getText( texts[ i ] );
      }
    }
    return rv;
  }


  /*
   * Liefert zu einem Schluessel den dazu passenden Tastencode
   * fuer das Mnemonic.
   * Dazu wird nach dem Schluessel mit dem Suffix ".mnemonic" gesucht.
   * Da getText(...) fuer ein nicht-null-Argument niemals null liefert,
   * sondern im Nichttrefferfall das Argument unveraendert zurueckgibt,
   * ist ein "nicht gefunden" hier die Zeichenkette
   * key + ".mnemonic" selbst, die praktisch nie genau ein Zeichen
   * lang ist. Die Pruefung auf Laenge 1 reicht also aus, um einen
   * echten Mnemonic-Treffer von einem Fehltreffer zu unterscheiden.
   */
  public static int mnemonic( String key, int defaultKey )
  {
    int rv = defaultKey;
    if( key != null ) {
      String translation = getText( key + MNEMONIC_SUFFIX );
      if( translation.length() == 1 ) {
	int keyCode = KeyEvent.getExtendedKeyCodeForChar(
						translation.charAt( 0 ) );
	if( keyCode != KeyEvent.VK_UNDEFINED ) {
	  rv = keyCode;
	}
      }
    }
    return rv;
  }


	/* --- private Methoden --- */

  private static void ensureInitialized()
  {
    if( !initialized ) {
      setLangCode( LANG_CODE_DE );
    }
  }


  /*
   * Sammeln aller angefragten Schluessel fuer die QS,
   * gesteuert ueber die System-Property "jkcemu.lang.collect".
   * Ist diese gesetzt, wird ihr Wert als Dateiname interpretiert,
   * in den beim Beenden der JVM alle angefragten Schluessel
   * zusammen mit dem Ergebnis (Treffer/kein Treffer) geschrieben werden.
   * Erfasst wird nur, was wie ein echter symbolischer Schluessel
   * aussieht (klein geschrieben, punktgegliedert), damit nicht
   * jeder beliebige, bereits fertige Anzeigetext den Bericht
   * verunreinigt.
   */
  private static void collect( String text, boolean hit )
  {
    if( isCollectEnabled()
	&& (text != null)
	&& KEY_PATTERN.matcher( text ).matches() ) {
      synchronized( collectLock ) {
	Boolean old = collectMap.get( text );
	if( (old == null) || (!old.booleanValue() && hit) ) {
	  collectMap.put( text, Boolean.valueOf( hit ) );
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
