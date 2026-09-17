/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Pruefwerkzeug fuer die Konsistenz der Sprachschluessel
 */

package jkcemu.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;


public class KeyCheck
{
  /*
   * Ein Schluessel besteht aus mindestens zwei durch Punkt
   * getrennten Segmenten, wobei jedes Segment nur aus
   * Kleinbuchstaben, Ziffern und Unterstrich besteht.
   */
  private static final Pattern KEY_PATTERN = Pattern.compile(
			"^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$" );

  /*
   * Zeichenkettenliterale werden nicht per Regulaerausdruck erkannt,
   * sondern mit einem einfachen linearen Scanner (siehe scanJavaFile).
   * Ein Regulaerausdruck mit wiederholter Gruppe (z.B. "(...)*") kann
   * bei Pattern in der JVM je verarbeitetem Zeichen einen rekursiven
   * Aufruf verursachen; bei einem laengeren, nicht durch ein
   * schliessendes Anfuehrungszeichen begrenzten Abschnitt (z.B. einem
   * "/*" innerhalb eines Zeichenkettenliterals, das ein rein
   * regexbasierter Kommentarentferner faelschlich als Kommentarbeginn
   * werten wuerde) fuehrt das zu einem StackOverflowError.
   * Der lineare Scanner kennt Kommentare, Zeichen- und
   * Zeichenkettenliterale gleichzeitig und ist deshalb robust
   * gegenueber beliebig grossen Dateien.
   */
  private static final int MAX_KEY_CANDIDATE_LEN = 200;

  private static final String MNEMONIC_CALL_PREFIX = "LangUtil.mnemonic(";

  private static final String MNEMONIC_SUFFIX = ".mnemonic";
  private static final String BASE_FILE_NAME  = "jkcemu.properties";


  public static void main( String[] args )
  {
    if( args.length != 2 ) {
      System.err.println(
		"Aufruf: KeyCheck <Quelltext-Verzeichnis>"
			+ " <Sprachdatei-Verzeichnis>" );
      System.exit( 1 );
      return;
    }

    File srcDir  = new File( args[ 0 ] );
    File langDir = new File( args[ 1 ] );

    File baseFile = new File( langDir, BASE_FILE_NAME );
    if( !baseFile.isFile() ) {
      System.err.println(
		"Basiskatalog " + baseFile.getPath() + " nicht gefunden" );
      System.exit( 1 );
      return;
    }

    Properties  baseProps = new Properties();
    InputStream in        = null;
    try {
      in = new FileInputStream( baseFile );
      baseProps.load( new InputStreamReader( in, "UTF-8" ) );
    }
    catch( IOException ex ) {
      System.err.println(
		"Basiskatalog " + baseFile.getPath()
			+ " kann nicht gelesen werden: " + ex.getMessage() );
      System.exit( 1 );
      return;
    }
    finally {
      if( in != null ) {
	try {
	  in.close();
	}
	catch( IOException ex ) {}
      }
    }

    Set<String> baseKeys      = new TreeSet<>( baseProps.stringPropertyNames() );
    Set<String> knownPrefixes = derivePrefixes( baseKeys );

    List<File> javaFiles = new ArrayList<>();
    collectJavaFiles( srcDir, javaFiles );

    Map<String,String> referenced    = new TreeMap<>();
    Map<String,String> mnemonicCalls = new TreeMap<>();

    for( File javaFile : javaFiles ) {
      scanJavaFile( javaFile, baseKeys, knownPrefixes, referenced, mnemonicCalls );
    }

    /*
     * (1) Im Quelltext referenzierte, aber im Basiskatalog
     * fehlende Schluessel
     */
    Map<String,String> missingKeys = new TreeMap<>();
    for( Map.Entry<String,String> e : referenced.entrySet() ) {
      if( !baseKeys.contains( e.getKey() ) ) {
	missingKeys.put( e.getKey(), e.getValue() );
      }
    }
    Map<String,String> missingMnemonics = new TreeMap<>();
    for( Map.Entry<String,String> e : mnemonicCalls.entrySet() ) {
      String wantedKey = e.getKey() + MNEMONIC_SUFFIX;
      if( !baseKeys.contains( wantedKey ) ) {
	missingMnemonics.put( e.getKey(), e.getValue() );
      }
    }
    int section1Count = missingKeys.size() + missingMnemonics.size();

    System.out.println(
	"=== (1) Im Quelltext referenzierte, aber im Basiskatalog"
		+ " fehlende Schluessel ===" );
    for( Map.Entry<String,String> e : missingKeys.entrySet() ) {
      System.out.println(
		"MISSING: " + e.getValue() + "  key=\"" + e.getKey() + "\"" );
    }
    for( Map.Entry<String,String> e : missingMnemonics.entrySet() ) {
      System.out.println(
		"MISSING .mnemonic: " + e.getValue()
			+ "  key=\"" + e.getKey() + "\""
			+ "  wants \"" + e.getKey() + MNEMONIC_SUFFIX + "\"" );
    }
    System.out.println( "Anzahl: " + section1Count );
    System.out.println();

    /*
     * (2) Im Basiskatalog vorhandene, aber nie referenzierte Schluessel
     */
    List<String> unusedKeys = new ArrayList<>();
    for( String key : baseKeys ) {
      String lookupKey = key;
      if( key.endsWith( MNEMONIC_SUFFIX ) ) {
	lookupKey = key.substring(
		0, key.length() - MNEMONIC_SUFFIX.length() );
      }
      if( !referenced.containsKey( lookupKey ) ) {
	unusedKeys.add( key );
      }
    }
    System.out.println(
	"=== (2) Im Basiskatalog vorhandene, aber nie referenzierte"
		+ " Schluessel ===" );
    for( String key : unusedKeys ) {
      System.out.println( "UNUSED: " + key );
    }
    System.out.println( "Anzahl: " + unusedKeys.size() );
    System.out.println();

    // (3) Katalogvergleich je Sprachdatei
    boolean anyExtra = checkLocales( langDir, baseKeys );

    if( (section1Count > 0) || anyExtra ) {
      System.exit( 1 );
    }
  }


	/* --- private Methoden --- */

  /*
   * Ermittelt aus den Basisschluesseln die Menge der tatsaechlich
   * verwendeten "Bereich.Kategorie."-Praefixe.
   */
  private static Set<String> derivePrefixes( Set<String> baseKeys )
  {
    Set<String> prefixes = new TreeSet<>();
    for( String key : baseKeys ) {
      String prefix = prefixOf( key );
      if( prefix != null ) {
	prefixes.add( prefix );
      }
    }
    return prefixes;
  }


  /*
   * Liefert aus einem Schluessel die ersten beiden durch Punkt
   * getrennten Segmente als Praefix zurueck, z.B.
   * "common.menu.file" -> "common.menu.".
   * Besitzt der Schluessel keinen Punkt, wird null zurueckgeliefert.
   */
  private static String prefixOf( String key )
  {
    String[] parts = key.split( "\\." );
    if( parts.length < 2 ) {
      return null;
    }
    return parts[ 0 ] + "." + parts[ 1 ] + ".";
  }


  private static void collectJavaFiles( File dir, List<File> out )
  {
    File[] entries = dir.listFiles();
    if( entries != null ) {
      Arrays.sort( entries );
      for( File entry : entries ) {
	if( entry.isDirectory() ) {
	  collectJavaFiles( entry, out );
	} else if( entry.getName().endsWith( ".java" ) ) {
	  out.add( entry );
	}
      }
    }
  }


  /*
   * Zerlegt die Datei in einem einzigen linearen Durchlauf und
   * erkennt dabei Kommentare, Zeichenliterale und
   * Zeichenkettenliterale gleichzeitig (siehe Erklaerung oben bei
   * MAX_KEY_CANDIDATE_LEN). Fuer jedes gefundene Zeichenkettenliteral,
   * das wie ein Schluessel aussieht, wird geprueft, ob unmittelbar
   * davor (ohne Leerraum) der Text "LangUtil.mnemonic(" steht;
   * in dem Fall gilt es als Mnemonic-Aufruf, sonst als gewoehnliche
   * Schluessel-Referenz.
   */
  private static void scanJavaFile(
			File               javaFile,
			Set<String>        baseKeys,
			Set<String>        knownPrefixes,
			Map<String,String> referenced,
			Map<String,String> mnemonicCalls )
  {
    String text;
    try {
      text = readFile( javaFile );
    }
    catch( IOException ex ) {
      System.err.println(
		"Warnung: Datei " + javaFile.getPath()
			+ " kann nicht gelesen werden: " + ex.getMessage() );
      return;
    }

    int           len             = text.length();
    int           i               = 0;
    int           line            = 1;
    boolean       inString        = false;
    StringBuilder literalBuf      = null;
    int           literalLine     = 1;
    StringBuilder trailingContext = new StringBuilder();

    while( i < len ) {
      char c = text.charAt( i );

      if( inString ) {
	if( (c == '\\') && ((i + 1) < len) ) {
	  literalBuf.append( c ).append( text.charAt( i + 1 ) );
	  i += 2;
	  continue;
	}
	if( c == '"' ) {
	  inString = false;
	  handleStringLiteral(
			unescape( literalBuf.toString() ), literalLine,
			trailingContext, javaFile, baseKeys, knownPrefixes,
			referenced, mnemonicCalls );
	  literalBuf = null;
	  i++;
	  continue;
	}
	if( c == '\n' ) {
	  line++;
	}
	literalBuf.append( c );
	i++;
	continue;
      }

      if( c == '\n' ) {
	line++;
	i++;
	trailingContext.setLength( 0 );
	continue;
      }
      if( (c == '/') && ((i + 1) < len) && (text.charAt( i + 1 ) == '/') ) {
	while( (i < len) && (text.charAt( i ) != '\n') ) {
	  i++;
	}
	continue;
      }
      if( (c == '/') && ((i + 1) < len) && (text.charAt( i + 1 ) == '*') ) {
	i += 2;
	while( ((i + 1) < len)
	       && !((text.charAt( i ) == '*') && (text.charAt( i + 1 ) == '/')) )
	{
	  if( text.charAt( i ) == '\n' ) {
	    line++;
	  }
	  i++;
	}
	i += 2;
	trailingContext.setLength( 0 );
	continue;
      }
      if( c == '\'' ) {
	// Zeichenliteral ueberspringen (auch bei enthaltenem Escape)
	i++;
	if( (i < len) && (text.charAt( i ) == '\\') ) {
	  i += 2;
	} else if( i < len ) {
	  i++;
	}
	if( (i < len) && (text.charAt( i ) == '\'') ) {
	  i++;
	}
	trailingContext.setLength( 0 );
	continue;
      }
      if( c == '"' ) {
	inString    = true;
	literalBuf  = new StringBuilder();
	literalLine = line;
	i++;
	continue;
      }
      if( !Character.isWhitespace( c ) ) {
	trailingContext.append( c );
	int overflow = trailingContext.length() - MNEMONIC_CALL_PREFIX.length();
	if( overflow > 0 ) {
	  trailingContext.delete( 0, overflow );
	}
      }
      i++;
    }
  }


  /*
   * Wertet ein einzelnes gefundenes Zeichenkettenliteral aus:
   * Steht unmittelbar davor "LangUtil.mnemonic(", wird es als
   * Mnemonic-Aufruf gewertet; anderenfalls, falls es wie ein
   * Schluessel aussieht, als gewoehnliche Referenz.
   * Sehr lange Literale (z.B. eingebetteter HTML-Text) werden erst
   * gar nicht gegen KEY_PATTERN geprueft, da ein echter Schluessel
   * immer kurz ist -- das haelt die Pruefung schnell und billig.
   */
  private static void handleStringLiteral(
			String             literal,
			int                line,
			StringBuilder      trailingContext,
			File               javaFile,
			Set<String>        baseKeys,
			Set<String>        knownPrefixes,
			Map<String,String> referenced,
			Map<String,String> mnemonicCalls )
  {
    if( literal.length() > MAX_KEY_CANDIDATE_LEN ) {
      return;
    }
    boolean isMnemonicArg = endsWithMnemonicPrefix( trailingContext );
    if( isMnemonicArg ) {
      if( !mnemonicCalls.containsKey( literal ) ) {
	mnemonicCalls.put( literal, javaFile.getPath() + ":" + line );
      }
      return;
    }
    if( KEY_PATTERN.matcher( literal ).matches() ) {
      boolean isReferenced =
			baseKeys.contains( literal )
				|| knownPrefixes.contains( prefixOf( literal ) );
      if( isReferenced && !referenced.containsKey( literal ) ) {
	referenced.put( literal, javaFile.getPath() + ":" + line );
      }
    }
  }


  private static boolean endsWithMnemonicPrefix( StringBuilder trailingContext )
  {
    int n = trailingContext.length();
    int m = MNEMONIC_CALL_PREFIX.length();
    if( n < m ) {
      return false;
    }
    for( int k = 0; k < m; k++ ) {
      if( trailingContext.charAt( n - m + k ) != MNEMONIC_CALL_PREFIX.charAt( k ) ) {
	return false;
      }
    }
    return true;
  }


  /*
   * Wandelt die in Java-Zeichenkettenliteralen ueblichen
   * Escape-Sequenzen (nur die fuer Schluessel bzw. Mnemonic-Text
   * relevanten) in die entsprechenden Zeichen um. Nicht bekannte
   * Escapes werden unveraendert (ohne den Backslash) uebernommen.
   */
  private static String unescape( String s )
  {
    if( s.indexOf( '\\' ) < 0 ) {
      return s;
    }
    StringBuilder buf = new StringBuilder( s.length() );
    int           len = s.length();
    int           i   = 0;
    while( i < len ) {
      char c = s.charAt( i );
      if( (c == '\\') && ((i + 1) < len) ) {
	char d = s.charAt( i + 1 );
	switch( d ) {
	  case 'n': buf.append( '\n' ); break;
	  case 't': buf.append( '\t' ); break;
	  case 'r': buf.append( '\r' ); break;
	  case '"': buf.append( '"' ); break;
	  case '\\': buf.append( '\\' ); break;
	  default: buf.append( d );
	}
	i += 2;
      } else {
	buf.append( c );
	i++;
      }
    }
    return buf.toString();
  }


  private static String readFile( File file ) throws IOException
  {
    byte[] data = Files.readAllBytes( file.toPath() );
    return new String( data, "UTF-8" );
  }


  /*
   * Vergleicht alle Sprachdateien "jkcemu_<code>.properties" im
   * angegebenen Verzeichnis mit dem Basiskatalog und gibt je Datei
   * die fehlenden und die zusaetzlichen Schluessel aus.
   * Rueckgabewert ist true, wenn mindestens eine Sprachdatei
   * zusaetzliche (im Basiskatalog nicht vorhandene) Schluessel hat.
   */
  private static boolean checkLocales( File langDir, Set<String> baseKeys )
  {
    boolean anyExtra = false;
    File[]  files     = langDir.listFiles();
    System.out.println( "=== (3) Katalogvergleich je Sprachdatei ===" );
    if( files != null ) {
      Arrays.sort( files );
      for( File file : files ) {
	String name = file.getName();
	if( name.startsWith( "jkcemu_" ) && name.endsWith( ".properties" ) ) {
	  if( checkOneLocale( file, baseKeys ) ) {
	    anyExtra = true;
	  }
	}
      }
    }
    return anyExtra;
  }


  private static boolean checkOneLocale( File file, Set<String> baseKeys )
  {
    Properties  localProps = new Properties();
    InputStream in         = null;
    try {
      in = new FileInputStream( file );
      localProps.load( new InputStreamReader( in, "UTF-8" ) );
    }
    catch( IOException ex ) {
      System.err.println(
		"Warnung: Sprachdatei " + file.getPath()
			+ " kann nicht gelesen werden: " + ex.getMessage() );
      return false;
    }
    finally {
      if( in != null ) {
	try {
	  in.close();
	}
	catch( IOException ex ) {}
      }
    }

    Set<String> localKeys = new TreeSet<>( localProps.stringPropertyNames() );

    Set<String> missing = new TreeSet<>( baseKeys );
    missing.removeAll( localKeys );

    Set<String> extra = new TreeSet<>( localKeys );
    extra.removeAll( baseKeys );

    System.out.println( "--- " + file.getName() + " ---" );
    System.out.println( "  fehlend: " + missing.size() );
    for( String key : missing ) {
      System.out.println( "    MISSING: " + key );
    }
    System.out.println( "  zusaetzlich: " + extra.size() );
    for( String key : extra ) {
      System.out.println( "    EXTRA: " + key );
    }

    return !extra.isEmpty();
  }


	/* --- Konstruktor --- */

  private KeyCheck()
  {
    // nicht instanziierbar
  }
}
