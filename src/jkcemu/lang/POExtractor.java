/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Werkzeug zum Erzeugen der PO-Vorlage (.pot) aus den Java-Quelltexten
 */

package jkcemu.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class POExtractor
{
  /*
   * Kennzeichnung der dritten Spalte in CALL_SITES (Argumentspezifikation):
   *   ARG_ALL   - jede Argumentgruppe, die eine reine Zeichenkette ist,
   *               wird als eigener Eintrag extrahiert
   *               (der Empfaenger bzw. die uebrigen Argumente
   *               sind bei diesen Aufrufen nie selbst Literale,
   *               so dass "alle Literale nehmen" unkritisch ist)
   *   ARG_CTX   - Sonderfall LangUtil.trCtx(context,text):
   *               das 1. Argument wird msgctxt, das 2. msgid
   *   "0","1","2",... - nur die Argumentgruppe mit diesem Index
   *               (nullbasiert) wird extrahiert, alle anderen
   *               Literale des Aufrufs werden zwar als "gesehen"
   *               markiert (kein Kandidat), aber nicht uebernommen.
   *               Das ist noetig, wenn an anderen Positionen
   *               desselben Aufrufs Ressourcennamen, Aktions-Kommandos
   *               o.ae. stehen, die keine uebersetzbaren Texte sind.
   */
  private static final String ARG_ALL = "*";
  private static final String ARG_CTX = "CTX";

  /*
   * Liste der Methodennamen (mit optionalem Klassenqualifizierer),
   * an denen ein uebersetzbarer Text als Aufrufargument erkannt wird.
   * Ein null-Qualifizierer bedeutet, dass der Methodenname
   * unabhaengig vom Empfaenger erkannt wird.
   * Diese Liste ist bewusst als einfaches Array gehalten,
   * damit sie leicht erweitert werden kann.
   */
  private static final String[][] CALL_SITES = {
	{ "GUIFactory", "createLabel",                       ARG_ALL },
	{ "GUIFactory", "createButton",                      ARG_ALL },
	{ "GUIFactory", "createCheckBox",                     ARG_ALL },
	{ "GUIFactory", "createRadioButton",                  ARG_ALL },
	{ "GUIFactory", "createMenu",                         ARG_ALL },
	{ "GUIFactory", "createMenuItem",                     ARG_ALL },
	{ "GUIFactory", "createCheckBoxMenuItem",             ARG_ALL },
	{ "GUIFactory", "createRadioButtonMenuItem",          ARG_ALL },
	{ "GUIFactory", "createTitledBorder",                 ARG_ALL },
	{ "GUIFactory", "createImageButton",                  "2" },
	{ "GUIFactory", "createRelImageResourceButton",       "2" },
	{ null,         "createButton",                       ARG_ALL },
	{ null,         "createMenuItem",                     ARG_ALL },
	{ null,         "createMenuItemWithStandardAccelerator", ARG_ALL },
	{ null,         "createMenuItemWithNonControlAccelerator", ARG_ALL },
	{ null,         "createMenuItemWithDirectAccelerator", ARG_ALL },
	{ null,         "createPopupMenuItem",                "1" },
	{ "FileUtil",   "showFileOpenDlg",                    "1" },
	{ "FileUtil",   "showFileSaveDlg",                    "1" },
	{ null,         "askForOutputFile",                   "1" },
	{ null,         "addTab",                             ARG_ALL },
	{ null,         "setTitle",                           ARG_ALL },
	{ null,         "setText",                            ARG_ALL },
	{ null,         "setToolTipText",                     ARG_ALL },
	{ null,         "showErrorDlg",                       ARG_ALL },
	{ null,         "showInfoDlg",                        ARG_ALL },
	{ null,         "showYesNoDlg",                       ARG_ALL },
	{ null,         "showSuppressableInfoDlg",             ARG_ALL },
	{ null,         "showOptionDlg",                      ARG_ALL },
	{ null,         "addJMenuItem",                       "0" },
	{ null,         "addJMenuItemWithControlShortcut",    "0" },
	{ null,         "appendToLog",                        ARG_ALL },
	{ null,         "putWarning",                         ARG_ALL },
	{ null,         "fireError",                          ARG_ALL },
	{ "LangUtil",   "tr",                                 ARG_ALL },
	{ "LangUtil",   "trCtx",                              ARG_CTX } };

  /*
   * Liste der Klassen, deren Konstruktoraufruf ("new Klasse( ... )")
   * einen uebersetzbaren Text enthaelt.
   * Bei den Exception-Klassen ist das die Fehlermeldung,
   * bei den Komponenten der Beschriftungstext, den der Konstruktor
   * an GUIFactory bzw. setTitle weiterreicht.
   * Die Argumentspezifikation entspricht der in CALL_SITES;
   * mehrere Argumentindizes werden durch Komma getrennt.
   */
  private static final String[][] NEW_CALL_SITES = {
	{ "IOException",            "0" },
	{ "PrgException",           "0" },
	{ "UserInputException",     "0" },
	{ "FontSelectionFld",       "1" },
	{ "ROMFileSettingsFld",     "2" },
	{ "RAMFloppySettingsFld",   "2" },
	{ "RAMFloppiesSettingsFld", "2,4" },
	{ "ReplyBytesDlg",          "1" },
	{ "SaveDlg",                "3" } };

  private static final String DEFAULT_OUTPUT     = "src/lang/jkcemu.pot";
  private static final String CANDIDATES_FILE    = "candidates.txt";

  private static final String PREFIX_TEXT_FIELD = "TEXT_";

  /*
   * Namen von String-Array-Feldern, deren Elemente uebersetzbare
   * Texte sind (Spaltenueberschriften der Tabellenmodelle).
   * Die Uebersetzung erfolgt erst beim Zugriff (getColumnName),
   * da die Felder statisch initialisiert werden,
   * also bevor die Sprache feststeht.
   */
  private static final String[] TEXT_ARRAY_FIELDS = { "colNames" };


  public static void main( String[] args )
  {
    if( args.length < 1 ) {
      System.err.println(
	"Aufruf: POExtractor <Quellverzeichnis> [<Ausgabedatei>]" );
      System.exit( 1 );
      return;
    }
    File srcDir  = new File( args[ 0 ] );
    File outFile = new File( args.length > 1 ? args[ 1 ] : DEFAULT_OUTPUT );
    File outDir  = outFile.getParentFile();
    File candFile = new File(
			outDir != null ? outDir : new File( "." ),
			CANDIDATES_FILE );

    POExtractor      extractor  = new POExtractor();
    List<Candidate>  candidates = new ArrayList<>();
    Map<String,POTEntry> map    = extractor.extractDir( srcDir, candidates );
    List<POTEntry>   entries    = new ArrayList<>( map.values() );
    Collections.sort( entries, new POTEntryComparator() );
    Collections.sort( candidates, new CandidateComparator() );
    try {
      extractor.write( outFile, entries );
      extractor.writeCandidates( candFile, candidates );
      System.out.println(
	entries.size() + " eindeutige Texte extrahiert -> " + outFile );
      System.out.println(
	candidates.size()
		+ " unklare Fundstellen (Kandidaten) -> " + candFile );
    }
    catch( IOException ex ) {
      System.err.println(
	"Ausgabedatei kann nicht geschrieben werden: " + ex.getMessage() );
      System.exit( 1 );
    }
  }


	/* --- private Methoden --- */

  /*
   * Sucht rekursiv nach *.java-Dateien, wertet diese aus
   * und liefert die Rohliste aller Fundstellen zurueck.
   * Alle nicht ueber CALL_SITES/NEW_CALL_SITES/TEXT_-Felder
   * erreichten, aber deutsch aussehenden Zeichenketten
   * werden zusaetzlich in candidatesOut gesammelt.
   */
  private Map<String,POTEntry> extractDir(
		File            srcDir,
		List<Candidate> candidatesOut )
  {
    Map<String,POTEntry> result = new LinkedHashMap<>();
    List<File>           files  = new ArrayList<>();
    collectJavaFiles( srcDir, files );
    Collections.sort( files, new FileComparator() );
    for( File file : files ) {
      String relPath = relativePath( srcDir, file );
      try {
	String      content  = readFile( file );
	List<Token> tokens   = tokenize( content );
	boolean[]   consumed = new boolean[ tokens.size() ];
	extractFromTokens( tokens, relPath, result, consumed );
	collectCandidates( tokens, consumed, relPath, candidatesOut );
      }
      catch( IOException ex ) {
	System.err.println(
		"Datei " + file + " kann nicht gelesen werden: "
			+ ex.getMessage() );
      }
    }
    return result;
  }


  private static void collectJavaFiles( File dir, List<File> files )
  {
    File[] entries = dir.listFiles();
    if( entries != null ) {
      for( File entry : entries ) {
	if( entry.isDirectory() ) {
	  collectJavaFiles( entry, files );
	} else if( entry.getName().endsWith( ".java" ) ) {
	  files.add( entry );
	}
      }
    }
  }


  private static String relativePath( File baseDir, File file )
  {
    String basePath = baseDir.getAbsolutePath().replace( '\\', '/' );
    String filePath = file.getAbsolutePath().replace( '\\', '/' );
    if( filePath.startsWith( basePath ) ) {
      filePath = filePath.substring( basePath.length() );
      if( filePath.startsWith( "/" ) ) {
	filePath = filePath.substring( 1 );
      }
    }
    return filePath;
  }


  private static String readFile( File file ) throws IOException
  {
    StringBuilder buf = new StringBuilder( (int) file.length() + 16 );
    InputStream   in  = null;
    try {
      in = new FileInputStream( file );
      Reader reader = new InputStreamReader( in, "UTF-8" );
      char[] chunk  = new char[ 0x1000 ];
      int    n;
      while( (n = reader.read( chunk )) != -1 ) {
	buf.append( chunk, 0, n );
      }
    }
    finally {
      if( in != null ) {
	try {
	  in.close();
	}
	catch( IOException ex ) {}
      }
    }
    return buf.toString();
  }


  private void addEntry(
		Map<String,POTEntry> map,
		String                context,
		String                msgId,
		String                relPath,
		int                   line )
  {
    if( (msgId != null) && !msgId.isEmpty() ) {
      String   key   = (context != null ? context + '\u0004' + msgId : msgId);
      POTEntry entry = map.get( key );
      if( entry == null ) {
	entry = new POTEntry( context, msgId );
	map.put( key, entry );
      }
      entry.addReference( relPath, line );
    }
  }


  private void extractFromTokens(
		List<Token>           tokens,
		String                relPath,
		Map<String,POTEntry>  result,
		boolean[]             consumed )
  {
    int n = tokens.size();
    for( int i = 0; i < n; i++ ) {
      Token t = tokens.get( i );

      // "static final String TEXT_xxx = ...;"-Deklarationen
      if( (t.type == TokType.IDENT) && t.text.equals( "String" )
	  && ((i + 1) < n)
	  && (tokens.get( i + 1 ).type == TokType.IDENT)
	  && tokens.get( i + 1 ).text.startsWith( PREFIX_TEXT_FIELD )
	  && ((i + 2) < n)
	  && (tokens.get( i + 2 ).type == TokType.EQUALS) )
      {
	ParsedLiteral lit = parseStringExpr( tokens, i + 3 );
	if( (lit != null)
	    && ((i + 3 + lit.length) < n)
	    && (tokens.get( i + 3 + lit.length ).type == TokType.SEMI) )
	{
	  markConsumed( consumed, i + 3, lit.length );
	  addEntry( result, null, lit.value, relPath, lit.line );
	}
	continue;
      }

      /*
       * "String[] colNames = { ... };"-Deklarationen
       * Die Klammern werden vom Lexer als OTHER geliefert;
       * es genuegt deshalb, ab dem Gleichheitszeichen
       * alle Zeichenkettenliterale bis zum Semikolon einzusammeln.
       */
      if( (t.type == TokType.IDENT) && t.text.equals( "String" ) ) {
	int k = i + 1;
	while( (k < n) && (tokens.get( k ).type == TokType.OTHER) ) {
	  k++;
	}
	if( (k > i + 1)
	    && (k < n)
	    && (tokens.get( k ).type == TokType.IDENT)
	    && isTextArrayField( tokens.get( k ).text )
	    && ((k + 1) < n)
	    && (tokens.get( k + 1 ).type == TokType.EQUALS) )
	{
	  int j = k + 2;
	  while( (j < n) && (tokens.get( j ).type != TokType.SEMI) ) {
	    if( tokens.get( j ).type == TokType.STRING ) {
	      ParsedLiteral lit = parseStringExpr( tokens, j );
	      if( lit != null ) {
		markConsumed( consumed, j, lit.length );
		addEntry( result, null, lit.value, relPath, lit.line );
		j += lit.length;
		continue;
	      }
	    }
	    j++;
	  }
	  i = j;
	  continue;
	}
      }

      // Konstruktoraufrufe: "new Klasse( ... )"
      if( (t.type == TokType.IDENT) && t.text.equals( "new" )
	  && ((i + 2) < n)
	  && (tokens.get( i + 1 ).type == TokType.IDENT)
	  && (tokens.get( i + 2 ).type == TokType.LPAREN) )
      {
	String argSpec = newCallArgSpec( tokens.get( i + 1 ).text );
	if( argSpec != null ) {
	  extractCallArgs(
			tokens,
			i + 2,
			argSpec,
			result,
			relPath,
			consumed );
	}
      }

      // Aufruf-Erkennung: [Qualifizierer '.'] Methodenname '('
      if( t.type == TokType.IDENT ) {
	String qualifier   = null;
	String methodName  = null;
	int    lparenIdx   = -1;
	if( ((i + 3) < n)
	    && (tokens.get( i + 1 ).type == TokType.DOT)
	    && (tokens.get( i + 2 ).type == TokType.IDENT)
	    && (tokens.get( i + 3 ).type == TokType.LPAREN) )
	{
	  qualifier  = t.text;
	  methodName = tokens.get( i + 2 ).text;
	  lparenIdx  = i + 3;
	} else if( ((i + 1) < n)
		   && (tokens.get( i + 1 ).type == TokType.LPAREN)
		   && ((i == 0)
		       || (tokens.get( i - 1 ).type != TokType.DOT)) )
	{
	  /*
	   * Nur als unqualifizierter Aufruf werten,
	   * wenn dem Namen kein Punkt vorausgeht.
	   * Anderenfalls wurde der Aufruf bereits
	   * ueber den Qualifizierer (siehe oben) erkannt,
	   * und die Fundstelle wuerde sonst doppelt
	   * aufgenommen werden.
	   */
	  methodName = t.text;
	  lparenIdx  = i + 1;
	}
	if( methodName != null ) {
	  String argSpec = callSiteArgSpec( qualifier, methodName );
	  if( argSpec != null ) {
	    extractCallArgs(
			tokens,
			lparenIdx,
			argSpec,
			result,
			relPath,
			consumed );
	  }
	}
      }
    }
  }


  private static String callSiteArgSpec( String qualifier, String methodName )
  {
    String rv = null;
    for( int i = 0; i < CALL_SITES.length; i++ ) {
      String q = CALL_SITES[ i ][ 0 ];
      String m = CALL_SITES[ i ][ 1 ];
      if( m.equals( methodName )
	  && ((q == null) || q.equals( qualifier )) )
      {
	rv = CALL_SITES[ i ][ 2 ];
	break;
      }
    }
    return rv;
  }


  private static String newCallArgSpec( String className )
  {
    String rv = null;
    for( int i = 0; i < NEW_CALL_SITES.length; i++ ) {
      if( NEW_CALL_SITES[ i ][ 0 ].equals( className ) ) {
	rv = NEW_CALL_SITES[ i ][ 1 ];
	break;
      }
    }
    return rv;
  }


  /*
   * Wertet die Argumentliste eines erkannten Aufrufs aus.
   * Jede Argumentgruppe, die ausschliesslich aus einer
   * (ggf. mit '+' verketteten) Zeichenkette besteht,
   * wird als Literal erkannt und - unabhaengig davon,
   * ob sie tatsaechlich uebernommen wird - als "gesehen"
   * markiert (siehe consumed), damit sie nicht spaeter
   * faelschlich in candidates.txt auftaucht.
   * Welche der erkannten Literale tatsaechlich als
   * uebersetzbarer Text uebernommen werden, richtet sich
   * nach argSpec (ARG_ALL, ARG_CTX oder ein Argumentindex).
   */
  private void extractCallArgs(
		List<Token>           tokens,
		int                   lparenIdx,
		String                argSpec,
		Map<String,POTEntry>  result,
		String                relPath,
		boolean[]             consumed )
  {
    int          n     = tokens.size();
    int          depth = 0;
    int          start = lparenIdx + 1;
    List<int[]>  groups = new ArrayList<>();
    for( int i = lparenIdx; i < n; i++ ) {
      TokType ty = tokens.get( i ).type;
      if( ty == TokType.LPAREN ) {
	depth++;
      } else if( ty == TokType.RPAREN ) {
	depth--;
	if( depth == 0 ) {
	  groups.add( new int[] { start, i } );
	  break;
	}
      } else if( (ty == TokType.COMMA) && (depth == 1) ) {
	groups.add( new int[] { start, i } );
	start = i + 1;
      }
    }

    List<String>  literals = new ArrayList<>();
    List<Integer> lines    = new ArrayList<>();
    for( int[] g : groups ) {
      String value = null;
      int    line  = -1;
      if( g[ 0 ] < g[ 1 ] ) {
	ParsedLiteral lit = parseStringExpr( tokens, g[ 0 ] );
	if( (lit != null) && ((g[ 0 ] + lit.length) == g[ 1 ]) ) {
	  value = lit.value;
	  line  = lit.line;
	  markConsumed( consumed, g[ 0 ], lit.length );
	}
      }
      literals.add( value );
      lines.add( Integer.valueOf( line ) );
    }

    if( ARG_CTX.equals( argSpec ) ) {
      if( (literals.size() >= 2)
	  && (literals.get( 0 ) != null)
	  && (literals.get( 1 ) != null) )
      {
	addEntry(
		result,
		literals.get( 0 ),
		literals.get( 1 ),
		relPath,
		lines.get( 1 ).intValue() );
      }
    } else if( ARG_ALL.equals( argSpec ) ) {
      for( int i = 0; i < literals.size(); i++ ) {
	String s = literals.get( i );
	if( s != null ) {
	  addEntry( result, null, s, relPath, lines.get( i ).intValue() );
	}
      }
    } else {
      String[] specs = argSpec.split( "," );
      for( int i = 0; i < specs.length; i++ ) {
	int idx = -1;
	try {
	  idx = Integer.parseInt( specs[ i ].trim() );
	}
	catch( NumberFormatException ex ) {}
	if( (idx >= 0) && (idx < literals.size()) ) {
	  String s = literals.get( idx );
	  if( s != null ) {
	    addEntry( result, null, s, relPath, lines.get( idx ).intValue() );
	  }
	}
      }
    }
  }


  private static boolean isTextArrayField( String fieldName )
  {
    boolean rv = false;
    for( int i = 0; i < TEXT_ARRAY_FIELDS.length; i++ ) {
      if( TEXT_ARRAY_FIELDS[ i ].equals( fieldName ) ) {
	rv = true;
	break;
      }
    }
    return rv;
  }


  private static void markConsumed(
		boolean[] consumed,
		int       start,
		int       length )
  {
    int end = start + length;
    if( end > consumed.length ) {
      end = consumed.length;
    }
    for( int i = start; i < end; i++ ) {
      consumed[ i ] = true;
    }
  }


  /*
   * Versucht, ab dem angegebenen Token-Index eine reine
   * Zeichenkette (ggf. mit '+' verkettet) zu parsen.
   * Liefert null, wenn an der Position keine Zeichenkette beginnt.
   */
  private static ParsedLiteral parseStringExpr(
		List<Token> tokens,
		int         start )
  {
    int n = tokens.size();
    if( (start >= n) || (tokens.get( start ).type != TokType.STRING) ) {
      return null;
    }
    StringBuilder buf  = new StringBuilder( tokens.get( start ).text );
    int           line = tokens.get( start ).line;
    int           idx  = start + 1;
    while( ((idx + 1) < n)
	   && (tokens.get( idx ).type == TokType.PLUS)
	   && (tokens.get( idx + 1 ).type == TokType.STRING) )
    {
      buf.append( tokens.get( idx + 1 ).text );
      idx += 2;
    }
    ParsedLiteral rv = new ParsedLiteral();
    rv.value  = buf.toString();
    rv.line   = line;
    rv.length = idx - start;
    return rv;
  }


	/* --- Kandidatensuche (Texte ohne bekannte Fundstelle) --- */

  /*
   * Durchsucht die Tokens einer Datei nach Zeichenketten,
   * die nicht bereits ueber einen bekannten Aufruf (CALL_SITES,
   * NEW_CALL_SITES) oder ein TEXT_-Feld erfasst wurden
   * (siehe consumed) und die deutsch aussehen.
   * Ergebnis ist eine reine Handreichung fuer die manuelle
   * Nachpflege - hier wird bewusst NICHT automatisch
   * in CALL_SITES aufgenommen, da z.B. .append(...)-Aufrufe
   * in den Compilern auch Z80-Assemblertext oder HTML
   * transportieren, der niemals uebersetzt werden darf.
   */
  private static void collectCandidates(
		List<Token>     tokens,
		boolean[]       consumed,
		String          relPath,
		List<Candidate> out )
  {
    int n = tokens.size();
    for( int i = 0; i < n; i++ ) {
      Token t = tokens.get( i );
      if( t.type != TokType.STRING ) {
	continue;
      }
      /*
       * Nur am Beginn einer reinen Verkettungskette pruefen,
       * d.h. ueberspringen, wenn dieses Token die Fortsetzung
       * einer bereits an frueherer Stelle betrachteten
       * String+String-Verkettung ist. Steht vor dem '+' dagegen
       * keine Zeichenkette (z.B. bei "a " + var + " b"), handelt
       * es sich um ein eigenstaendiges Fragment und wird
       * NICHT uebersprungen.
       */
      if( (i > 1)
	  && (tokens.get( i - 1 ).type == TokType.PLUS)
	  && (tokens.get( i - 2 ).type == TokType.STRING) )
      {
	continue;
      }
      if( consumed[ i ] ) {
	continue;
      }
      ParsedLiteral lit = parseStringExpr( tokens, i );
      if( (lit != null) && isCandidateText( lit.value ) ) {
	out.add(
		new Candidate(
			relPath,
			lit.line,
			enclosingCallName( tokens, i ),
			lit.value ) );
      }
    }
  }


  /*
   * "Deutsch aussehend" = enthaelt einen Umlaut/ein scharfes S
   * oder mindestens zwei kleingeschriebene Woerter mit
   * mindestens 4 Buchstaben.
   * Ausgeschlossen werden Zeichenketten mit Tabulator,
   * die mit '<' beginnen (HTML) oder wie ein Pfad-
   * bzw. Ressourcenname aussehen.
   */
  private static boolean isCandidateText( String s )
  {
    if( (s == null) || s.isEmpty() ) {
      return false;
    }
    if( (s.indexOf( '\t' ) >= 0) || s.startsWith( "<" ) ) {
      return false;
    }
    if( looksLikePathOrResource( s ) ) {
      return false;
    }
    return containsUmlaut( s ) || hasTwoLowerCaseWords( s );
  }


  private static boolean containsUmlaut( String s )
  {
    int len = s.length();
    for( int i = 0; i < len; i++ ) {
      char c = s.charAt( i );
      if( (c == '\u00C4') || (c == '\u00D6') || (c == '\u00DC')
	  || (c == '\u00E4') || (c == '\u00F6') || (c == '\u00FC')
	  || (c == '\u00DF') )
      {
	return true;
      }
    }
    return false;
  }


  private static boolean hasTwoLowerCaseWords( String s )
  {
    int wordCount = 0;
    int len       = s.length();
    int i         = 0;
    while( i < len ) {
      if( Character.isLowerCase( s.charAt( i ) ) ) {
	int start = i;
	while( (i < len) && Character.isLowerCase( s.charAt( i ) ) ) {
	  i++;
	}
	if( (i - start) >= 4 ) {
	  wordCount++;
	  if( wordCount >= 2 ) {
	    return true;
	  }
	}
      } else {
	i++;
      }
    }
    return false;
  }


  private static boolean looksLikePathOrResource( String s )
  {
    if( containsWhitespace( s ) ) {
      return false;
    }
    if( (s.indexOf( '/' ) >= 0) || (s.indexOf( '\\' ) >= 0) ) {
      return true;
    }
    // z.B. "jkcemu.sram.init" oder "open.png"
    int dotPos = s.indexOf( '.' );
    if( (dotPos > 0) && (dotPos < (s.length() - 1)) ) {
      return true;
    }
    return false;
  }


  private static boolean containsWhitespace( String s )
  {
    int len = s.length();
    for( int i = 0; i < len; i++ ) {
      if( Character.isWhitespace( s.charAt( i ) ) ) {
	return true;
      }
    }
    return false;
  }


  /*
   * Ermittelt zu einer Token-Position den Namen des umschliessenden
   * Aufrufs (bzw. Konstruktors), indem rueckwaerts nach der
   * dazugehoerigen oeffnenden Klammer gesucht wird.
   * Dient nur der besseren Lesbarkeit von candidates.txt.
   */
  private static String enclosingCallName( List<Token> tokens, int stringIdx )
  {
    int depth = 0;
    for( int i = stringIdx - 1; i >= 0; i-- ) {
      TokType ty = tokens.get( i ).type;
      if( ty == TokType.RPAREN ) {
	depth++;
      } else if( ty == TokType.LPAREN ) {
	if( depth == 0 ) {
	  if( (i > 0) && (tokens.get( i - 1 ).type == TokType.IDENT) ) {
	    String name = tokens.get( i - 1 ).text;
	    if( (i > 1) && (tokens.get( i - 2 ).type == TokType.IDENT)
		&& "new".equals( tokens.get( i - 2 ).text ) )
	    {
	      return "new " + name;
	    }
	    if( (i > 2) && (tokens.get( i - 2 ).type == TokType.DOT)
		&& (tokens.get( i - 3 ).type == TokType.IDENT) )
	    {
	      return tokens.get( i - 3 ).text + "." + name;
	    }
	    return name;
	  }
	  return "?";
	}
	depth--;
      }
    }
    return "-";
  }


	/* --- Ausgabe --- */

  private void write( File outFile, List<POTEntry> entries )
						throws IOException
  {
    File parent = outFile.getParentFile();
    if( (parent != null) && !parent.exists() ) {
      parent.mkdirs();
    }
    PrintWriter out = null;
    try {
      out = new PrintWriter(
		new OutputStreamWriter(
			new FileOutputStream( outFile ), "UTF-8" ) );
      out.println( "# JKCEMU Sprachvorlage" );
      out.println( "# automatisch erzeugt von jkcemu.lang.POExtractor" );
      out.println( "msgid \"\"" );
      out.println( "msgstr \"\"" );
      out.println( "\"Project-Id-Version: JKCEMU\\n\"" );
      out.println( "\"MIME-Version: 1.0\\n\"" );
      out.println( "\"Content-Type: text/plain; charset=UTF-8\\n\"" );
      out.println( "\"Content-Transfer-Encoding: 8bit\\n\"" );
      out.println();
      for( POTEntry entry : entries ) {
	for( String ref : entry.references ) {
	  out.println( "#: " + ref );
	}
	if( entry.context != null ) {
	  out.println( "msgctxt \"" + escape( entry.context ) + "\"" );
	}
	out.println( "msgid \"" + escape( entry.msgId ) + "\"" );
	out.println( "msgstr \"\"" );
	out.println();
      }
    }
    finally {
      if( out != null ) {
	out.close();
      }
    }
  }


  /*
   * Schreibt die Liste der nicht eindeutig zuordenbaren
   * Fundstellen (siehe collectCandidates) als einfache,
   * tabulatorgetrennte Textdatei fuer die manuelle Nachpflege.
   * Bewusst kein PO-Format, damit diese Datei nicht versehentlich
   * als Uebersetzungskatalog geladen werden kann.
   */
  private void writeCandidates( File outFile, List<Candidate> candidates )
						throws IOException
  {
    File parent = outFile.getParentFile();
    if( (parent != null) && !parent.exists() ) {
      parent.mkdirs();
    }
    PrintWriter out = null;
    try {
      out = new PrintWriter(
		new OutputStreamWriter(
			new FileOutputStream( outFile ), "UTF-8" ) );
      out.println(
	"# Nicht ueber CALL_SITES erreichte, deutsch aussehende Texte." );
      out.println(
	"# Format: Datei:Zeile<TAB>umschliessender Aufruf<TAB>Text" );
      out.println(
	"# Manuell pruefen und ggf. in CALL_SITES bzw. LangUtil.tr(...)"
		+ " einarbeiten." );
      for( Candidate c : candidates ) {
	out.print( c.relPath );
	out.print( ':' );
	out.print( c.line );
	out.print( '\t' );
	out.print( c.enclosingCall );
	out.print( '\t' );
	out.println( escape( c.text ) );
      }
    }
    finally {
      if( out != null ) {
	out.close();
      }
    }
  }


  private static String escape( String s )
  {
    StringBuilder buf = new StringBuilder( s.length() + 8 );
    int           len = s.length();
    for( int i = 0; i < len; i++ ) {
      char ch = s.charAt( i );
      switch( ch ) {
	case '\\':
	  buf.append( "\\\\" );
	  break;
	case '\"':
	  buf.append( "\\\"" );
	  break;
	case '\n':
	  buf.append( "\\n" );
	  break;
	case '\t':
	  buf.append( "\\t" );
	  break;
	case '\r':
	  buf.append( "\\r" );
	  break;
	default:
	  if( ch < ' ' ) {
	    /*
	     * Sonstiges Steuerzeichen (z.B. in Formatkennungen wie
	     * FileInfo.CSW_MAGIC enthalten): als \\uXXXX ausgeben,
	     * damit die Ausgabedatei ein sauberer, mit Standard-
	     * Werkzeugen (grep, less, ...) lesbarer Text bleibt.
	     */
	    buf.append( "\\u" );
	    String hex = Integer.toHexString( ch );
	    for( int k = hex.length(); k < 4; k++ ) {
	      buf.append( '0' );
	    }
	    buf.append( hex );
	  } else {
	    buf.append( ch );
	  }
      }
    }
    return buf.toString();
  }


	/* --- Java-Lexer --- */

  private enum TokType {
	STRING, IDENT, LPAREN, RPAREN, COMMA, DOT, PLUS, EQUALS, SEMI, OTHER };


  private static class Token
  {
    private TokType type;
    private String  text;
    private int     line;

    private Token( TokType type, String text, int line )
    {
      this.type = type;
      this.text = text;
      this.line = line;
    }
  };


  private static class ParsedLiteral
  {
    private String value;
    private int    line;
    private int    length;
  };


  /*
   * Zerlegt den Quelltext einer Java-Datei in Tokens.
   * Kommentare und Zeichenliteralen werden ueberlesen,
   * Zeichenketten werden inklusive Escape-Sequenzen dekodiert.
   * Eine einfache regelbasierte Erkennung reicht hier nicht aus,
   * da im Quelltext haeufig Zeichenliterale wie '"' vorkommen,
   * die sonst mit dem Beginn einer Zeichenkette verwechselt wuerden.
   */
  private static List<Token> tokenize( String content )
  {
    List<Token> tokens = new ArrayList<>();
    int         n       = content.length();
    int         i       = 0;
    int         line    = 1;
    while( i < n ) {
      char c = content.charAt( i );
      if( c == '\n' ) {
	line++;
	i++;
	continue;
      }
      if( Character.isWhitespace( c ) ) {
	i++;
	continue;
      }
      if( (c == '/') && ((i + 1) < n) && (content.charAt( i + 1 ) == '/') ) {
	i += 2;
	while( (i < n) && (content.charAt( i ) != '\n') ) {
	  i++;
	}
	continue;
      }
      if( (c == '/') && ((i + 1) < n) && (content.charAt( i + 1 ) == '*') ) {
	i += 2;
	while( ((i + 1) < n)
	       && !((content.charAt( i ) == '*')
		    && (content.charAt( i + 1 ) == '/')) )
	{
	  if( content.charAt( i ) == '\n' ) {
	    line++;
	  }
	  i++;
	}
	i += 2;
	continue;
      }
      if( c == '\'' ) {
	i++;
	while( (i < n) && (content.charAt( i ) != '\'') ) {
	  if( content.charAt( i ) == '\\' ) {
	    i++;
	    if( i < n ) {
	      i++;
	    }
	  } else {
	    if( content.charAt( i ) == '\n' ) {
	      line++;
	    }
	    i++;
	  }
	}
	i++;
	continue;
      }
      if( c == '\"' ) {
	int           startLine = line;
	StringBuilder val       = new StringBuilder();
	i++;
	while( (i < n) && (content.charAt( i ) != '\"') ) {
	  char d = content.charAt( i );
	  if( (d == '\\') && ((i + 1) < n) ) {
	    char e = content.charAt( i + 1 );
	    switch( e ) {
	      case 'n':
		val.append( '\n' );
		i += 2;
		break;
	      case 't':
		val.append( '\t' );
		i += 2;
		break;
	      case 'r':
		val.append( '\r' );
		i += 2;
		break;
	      case 'b':
		val.append( '\b' );
		i += 2;
		break;
	      case 'f':
		val.append( '\f' );
		i += 2;
		break;
	      case '\"':
		val.append( '\"' );
		i += 2;
		break;
	      case '\'':
		val.append( '\'' );
		i += 2;
		break;
	      case '\\':
		val.append( '\\' );
		i += 2;
		break;
	      case 'u':
		i += 2;
		if( (i + 4) <= n ) {
		  String hex = content.substring( i, i + 4 );
		  try {
		    val.append( (char) Integer.parseInt( hex, 16 ) );
		  }
		  catch( NumberFormatException ex ) {}
		  i += 4;
		}
		break;
	      default:
		val.append( e );
		i += 2;
	    }
	  } else {
	    if( d == '\n' ) {
	      line++;
	    }
	    val.append( d );
	    i++;
	  }
	}
	i++;
	tokens.add( new Token( TokType.STRING, val.toString(), startLine ) );
	continue;
      }
      if( Character.isJavaIdentifierStart( c ) ) {
	int start = i;
	i++;
	while( (i < n) && Character.isJavaIdentifierPart( content.charAt( i ) ) ) {
	  i++;
	}
	tokens.add(
		new Token( TokType.IDENT, content.substring( start, i ), line ) );
	continue;
      }
      switch( c ) {
	case '(':
	  tokens.add( new Token( TokType.LPAREN, null, line ) );
	  break;
	case ')':
	  tokens.add( new Token( TokType.RPAREN, null, line ) );
	  break;
	case ',':
	  tokens.add( new Token( TokType.COMMA, null, line ) );
	  break;
	case '.':
	  tokens.add( new Token( TokType.DOT, null, line ) );
	  break;
	case '+':
	  tokens.add( new Token( TokType.PLUS, null, line ) );
	  break;
	case '=':
	  tokens.add( new Token( TokType.EQUALS, null, line ) );
	  break;
	case ';':
	  tokens.add( new Token( TokType.SEMI, null, line ) );
	  break;
	default:
	  tokens.add( new Token( TokType.OTHER, null, line ) );
      }
      i++;
    }
    return tokens;
  }


	/* --- Hilfsklassen --- */

  private static class POTEntry
  {
    private String       context;
    private String       msgId;
    private List<String> references;
    private String       firstRefPath;
    private int          firstRefLine;

    private POTEntry( String context, String msgId )
    {
      this.context      = context;
      this.msgId        = msgId;
      this.references   = new ArrayList<>();
      this.firstRefPath = null;
      this.firstRefLine = 0;
    }

    private void addReference( String relPath, int line )
    {
      if( this.firstRefPath == null ) {
	this.firstRefPath = relPath;
	this.firstRefLine = line;
      }
      this.references.add( relPath + ":" + line );
    }
  };


  private static class Candidate
  {
    private String relPath;
    private int    line;
    private String enclosingCall;
    private String text;

    private Candidate(
		String relPath,
		int    line,
		String enclosingCall,
		String text )
    {
      this.relPath       = relPath;
      this.line           = line;
      this.enclosingCall = enclosingCall;
      this.text          = text;
    }
  };


  /*
   * Sortiert die Eintraege anhand ihrer ersten Fundstelle
   * (Dateiname, danach Zeilennummer), damit eine erneute
   * Extraktion ein stabiles Diff erzeugt.
   * Ein reiner String-Vergleich von "Datei:Zeile" waere hier falsch,
   * da z.B. "83" lexikalisch nach "124" einsortiert wuerde.
   */
  private static class POTEntryComparator implements Comparator<POTEntry>
  {
    @Override
    public int compare( POTEntry e1, POTEntry e2 )
    {
      int rv = e1.firstRefPath.compareTo( e2.firstRefPath );
      if( rv == 0 ) {
	rv = e1.firstRefLine - e2.firstRefLine;
      }
      return rv;
    }
  };


  private static class CandidateComparator implements Comparator<Candidate>
  {
    @Override
    public int compare( Candidate c1, Candidate c2 )
    {
      int rv = c1.relPath.compareTo( c2.relPath );
      if( rv == 0 ) {
	rv = c1.line - c2.line;
      }
      return rv;
    }
  };


  private static class FileComparator implements Comparator<File>
  {
    @Override
    public int compare( File f1, File f2 )
    {
      return f1.getAbsolutePath().compareTo( f2.getAbsolutePath() );
    }
  };
}
