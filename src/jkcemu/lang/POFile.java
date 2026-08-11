/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Einfacher Parser fuer PO-Dateien (gettext-Uebersetzungskataloge)
 */

package jkcemu.lang;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class POFile
{
  /*
   * Trennzeichen zwischen msgctxt und msgid im internen Schluessel,
   * angelehnt an die gettext-Konvention (Steuerzeichen EOT).
   */
  private static final char CONTEXT_SEP = '\u0004';


  public static class Entry
  {
    private String  context;
    private String  msgId;
    private String  msgStr;
    private boolean fuzzy;

    private Entry(
		String  context,
		String  msgId,
		String  msgStr,
		boolean fuzzy )
    {
      this.context = context;
      this.msgId   = msgId;
      this.msgStr  = msgStr;
      this.fuzzy   = fuzzy;
    }

    public String getContext()
    {
      return this.context;
    }

    public String getMsgId()
    {
      return this.msgId;
    }

    public String getMsgStr()
    {
      return this.msgStr;
    }

    public boolean isFuzzy()
    {
      return this.fuzzy;
    }

    /*
     * Ein Eintrag gilt nur dann als uebersetzt,
     * wenn er nicht als "fuzzy" markiert ist
     * und einen nicht leeren msgstr besitzt.
     */
    public boolean isTranslated()
    {
      return !this.fuzzy
		&& (this.msgStr != null)
		&& !this.msgStr.isEmpty();
    }
  };


  private Map<String,Entry> entriesByKey;
  private List<Entry>       entries;


  public POFile()
  {
    this.entriesByKey = new HashMap<>();
    this.entries      = new ArrayList<>();
  }


  public static POFile load( InputStream in ) throws IOException
  {
    POFile poFile = new POFile();
    poFile.parse( in );
    return poFile;
  }


  public Collection<Entry> getEntries()
  {
    return this.entries;
  }


  public int getTotalCount()
  {
    return this.entries.size();
  }


  public int getTranslatedCount()
  {
    int n = 0;
    for( Entry e : this.entries ) {
      if( e.isTranslated() ) {
	n++;
      }
    }
    return n;
  }


  /*
   * Liefert die Uebersetzung zu (context,msgId) zurueck.
   * Wenn context angegeben ist, aber dafuer kein uebersetzter Eintrag
   * existiert, wird ersatzweise der kontextlose Eintrag verwendet.
   * Existiert ueberhaupt kein uebersetzter Eintrag,
   * wird null zurueckgeliefert.
   */
  public String getTranslation( String context, String msgId )
  {
    if( msgId == null ) {
      return null;
    }
    Entry e = null;
    if( context != null ) {
      e = this.entriesByKey.get( context + CONTEXT_SEP + msgId );
    }
    if( (e == null) || !e.isTranslated() ) {
      Entry e2 = this.entriesByKey.get( msgId );
      if( (e2 != null) && e2.isTranslated() ) {
	e = e2;
      }
    }
    return ((e != null) && e.isTranslated() ? e.getMsgStr() : null);
  }


  /*
   * Werkzeug fuer den Ant-Target "lang-report":
   * Liest alle *.po-Dateien in dem uebergebenen Verzeichnis
   * und gibt je Datei die Anzahl der uebersetzten
   * und der insgesamt vorhandenen Eintraege aus.
   */
  public static void main( String[] args )
  {
    String dirName = (args.length > 0 ? args[ 0 ] : "src/lang");
    File   dir     = new File( dirName );
    File[] files   = dir.listFiles(
			new FilenameFilter()
			{
			  @Override
			  public boolean accept( File dir, String name )
			  {
			    return name.endsWith( ".po" );
			  }
			} );
    if( files != null ) {
      Arrays.sort( files );
      for( File file : files ) {
	InputStream in = null;
	try {
	  in            = new FileInputStream( file );
	  POFile poFile = POFile.load( in );
	  System.out.println(
		file.getName()
			+ ": "
			+ poFile.getTranslatedCount()
			+ "/"
			+ poFile.getTotalCount() );
	}
	catch( IOException ex ) {
	  System.err.println(
		file.getName() + ": Fehler beim Lesen: " + ex.getMessage() );
	}
	finally {
	  if( in != null ) {
	    try {
	      in.close();
	    }
	    catch( IOException ex ) {}
	  }
	}
      }
    }
  }


	/* --- private Methoden --- */

  private void addEntry(
		String  context,
		String  msgId,
		String  msgStr,
		boolean fuzzy )
  {
    // Kopfeintrag (leere msgid) wird uebersprungen
    if( (msgId == null) || msgId.isEmpty() ) {
      return;
    }
    Entry  entry = new Entry( context, msgId, msgStr, fuzzy );
    String key   = (context != null ? context + CONTEXT_SEP + msgId : msgId);
    this.entriesByKey.put( key, entry );
    this.entries.add( entry );
  }


  private void flushEntry(
		StringBuilder ctxBuf,
		StringBuilder msgIdBuf,
		StringBuilder msgStrBuf,
		boolean       fuzzy,
		boolean       haveEntry )
  {
    if( haveEntry ) {
      addEntry(
		(ctxBuf != null ? ctxBuf.toString() : null),
		(msgIdBuf != null ? msgIdBuf.toString() : null),
		(msgStrBuf != null ? msgStrBuf.toString() : null),
		fuzzy );
    }
  }


  private static boolean isKeyword( String s, String keyword )
  {
    boolean rv = false;
    if( s.startsWith( keyword ) ) {
      if( s.length() == keyword.length() ) {
	rv = true;
      } else {
	rv = Character.isWhitespace( s.charAt( keyword.length() ) );
      }
    }
    return rv;
  }


  private void parse( InputStream in ) throws IOException
  {
    BufferedReader reader = new BufferedReader(
				new InputStreamReader( in, "UTF-8" ) );

    StringBuilder ctxBuf    = null;
    StringBuilder msgIdBuf  = null;
    StringBuilder msgStrBuf = null;
    StringBuilder activeBuf = null;
    boolean       fuzzy     = false;
    boolean       haveEntry = false;

    String line;
    while( (line = reader.readLine()) != null ) {
      String s = line.trim();
      if( s.isEmpty() ) {
	flushEntry( ctxBuf, msgIdBuf, msgStrBuf, fuzzy, haveEntry );
	ctxBuf    = null;
	msgIdBuf  = null;
	msgStrBuf = null;
	activeBuf = null;
	fuzzy     = false;
	haveEntry = false;
	continue;
      }
      if( s.startsWith( "#" ) ) {
	if( s.startsWith( "#," ) && (s.indexOf( "fuzzy" ) >= 0) ) {
	  fuzzy = true;
	}
	activeBuf = null;
	continue;
      }
      if( isKeyword( s, "msgctxt" ) ) {
	ctxBuf    = new StringBuilder( unquote( s.substring( 7 ).trim() ) );
	activeBuf = ctxBuf;
	haveEntry = true;
	continue;
      }
      if( isKeyword( s, "msgid" ) ) {
	msgIdBuf  = new StringBuilder( unquote( s.substring( 5 ).trim() ) );
	activeBuf = msgIdBuf;
	haveEntry = true;
	continue;
      }
      if( isKeyword( s, "msgstr" ) ) {
	msgStrBuf = new StringBuilder( unquote( s.substring( 6 ).trim() ) );
	activeBuf = msgStrBuf;
	continue;
      }
      if( s.startsWith( "\"" ) && (activeBuf != null) ) {
	activeBuf.append( unquote( s ) );
	continue;
      }
      // unbekannte Zeile (z.B. msgid_plural, msgstr[n]) wird ignoriert
      activeBuf = null;
    }
    flushEntry( ctxBuf, msgIdBuf, msgStrBuf, fuzzy, haveEntry );
  }


  /*
   * Entfernt die umschliessenden Anfuehrungszeichen einer PO-Zeile
   * und wandelt die C-typischen Escape-Sequenzen um.
   */
  private static String unquote( String s )
  {
    int begin = s.indexOf( '"' );
    int end   = s.lastIndexOf( '"' );
    if( (begin < 0) || (end <= begin) ) {
      return "";
    }
    String        body = s.substring( begin + 1, end );
    int           len  = body.length();
    StringBuilder buf  = new StringBuilder( len );
    int           i    = 0;
    while( i < len ) {
      char ch = body.charAt( i );
      if( (ch == '\\') && ((i + 1) < len) ) {
	char nextCh = body.charAt( i + 1 );
	switch( nextCh ) {
	  case 'n':
	    buf.append( '\n' );
	    break;
	  case 't':
	    buf.append( '\t' );
	    break;
	  case 'r':
	    buf.append( '\r' );
	    break;
	  case '"':
	    buf.append( '\"' );
	    break;
	  case '\\':
	    buf.append( '\\' );
	    break;
	  default:
	    buf.append( nextCh );
	}
	i += 2;
      } else {
	buf.append( ch );
	i++;
      }
    }
    return buf.toString();
  }
}
