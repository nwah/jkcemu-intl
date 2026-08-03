/*
 * (c) 2008-2024 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Umwandler fuer Zeichensaetze
 */

package jkcemu.text;


public class CharConverter
{
  public static enum Encoding {
			ASCII,
			ISO646DE,
			CP437,
			CP850,
			ANSI,
			LATIN1 };

  public static final char REPLACEMENT_CHAR = '\uFFFD';


  private static final String cp437ToUnicode =
	"\u0000\u263A\u263B\u2665\u2666\u2663\u2660\u2022"
		+ "\u25D8\u25CB\u25D9\u2642\u2640\u266A\u266B\u263C"
		+ "\u25BA\u25C4\u2195\u203C\u00B6\u00A7\u25AC\u21A8"
		+ "\u2191\u2193\u2192\u2190\u221F\u2194\u25B2\u25BC"
		+ "\u0020!\"#$%&\'()*+,-./0123456789:;<=>?"
		+ "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_"
		+ "\u0060abcdefghijklmnopqrstuvwxyz{|}~\u2302"
		+ "\u00C7\u00FC\u00E9\u00E2\u00E4\u00E0\u00E5\u00E7"
		+ "\u00EA\u00EB\u00E8\u00EF\u00EE\u00EC\u00C4\u00C5"
		+ "\u00C9\u00E6\u00C6\u00F4\u00F6\u00F2\u00FB\u00F9"
		+ "\u00FF\u00D6\u00DC\u00A2\u00A3\u00A5\u20A7\u0192"
		+ "\u00E1\u00ED\u00F3\u00FA\u00F1\u00D1\u00AA\u00BA"
		+ "\u00BF\u2310\u00AC\u00BD\u00BC\u00A1\u00AB\u00BB"
		+ "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556"
		+ "\u2555\u2563\u2551\u2557\u255D\u255C\u255B\u2510"
		+ "\u2514\u2534\u252C\u251C\u2500\u253C\u255E\u255F"
		+ "\u255A\u2554\u2569\u2566\u2560\u2550\u256C\u2567"
		+ "\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256B"
		+ "\u256A\u2518\u250C\u2588\u2584\u258C\u2590\u2580"
		+ "\u03B1\u00DF\u0393\u03C0\u03A3\u03C3\u00B5\u03C4"
		+ "\u03A6\u0398\u03A9\u03B4\u221E\u03C6\u03B5\u2229"
		+ "\u2261\u00B1\u2265\u2264\u2320\u2321\u00F7\u2248"
		+ "\u00B0\u2219\u00B7\u221A\u207F\u00B2\u25A0\u00A0";

  private static final String cp850ToUnicode =
	"\u0000\u263A\u263B\u2665\u2666\u2663\u2660\u2022"
		+ "\u25D8\u25CB\u25D9\u2642\u2640\u266A\u266B\u263C"
		+ "\u25BA\u25C4\u2195\u203C\u00B6\u00A7\u25AC\u21A8"
		+ "\u2191\u2193\u2192\u2190\u221F\u2194\u25B2\u25BC"
		+ "\u0020!\"#$%&\'()*+,-./0123456789:;<=>?"
		+ "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_"
		+ "\u0060abcdefghijklmnopqrstuvwxyz{|}~\u2302"
		+ "\u00C7\u00FC\u00E9\u00E2\u00E4\u00E0\u00E5\u00E7"
		+ "\u00EA\u00EB\u00E8\u00EF\u00EE\u00EC\u00C4\u00C5"
		+ "\u00C9\u00E6\u00C6\u00F4\u00F6\u00F2\u00FB\u00F9"
		+ "\u00FF\u00D6\u00DC\u00F8\u00A3\u00D8\u00D7\u0192"
		+ "\u00E1\u00ED\u00F3\u00FA\u00F1\u00D1\u00AA\u00BA"
		+ "\u00BF\u00AE\u00AC\u00BD\u00BC\u00A1\u00AB\u00BB"
		+ "\u2591\u2592\u2593\u2502\u2524\u00C1\u00C2\u00C0"
		+ "\u00A9\u2563\u2551\u2557\u255D\u00A2\u00A5\u2510"
		+ "\u2514\u2534\u252C\u251C\u2500\u253C\u00E3\u00C3"
		+ "\u255A\u2554\u2569\u2566\u2560\u2550\u256C\u00A4"
		+ "\u00F0\u00D0\u00CA\u00CB\u00C8\u0131\u00CD\u00CE"
		+ "\u00CF\u2518\u250C\u2588\u2584\u00A6\u00CC\u2580"
		+ "\u00D3\u00DF\u00D4\u00D2\u00F5\u00D5\u00B5\u00FE"
		+ "\u00DE\u00DA\u00DB\u00D9\u00FD\u00DD\u00AF\u00B4"
		+ "\u00AD\u00B1\u2017\u00BE\u00B6\u00A7\u00F7\u00B8"
		+ "\u00B0\u00A8\u00B7\u00B9\u00B3\u00B2\u25A0\u00A0";


  private Encoding encoding;
  private String   encodingDisplayText;


  public CharConverter( Encoding encoding )
  {
    this.encoding = (encoding != null ? encoding : Encoding.ASCII);
    this.encodingDisplayText = getEncodingDisplayText( encoding );
  }


  public static CharConverter getCharConverter( Encoding encoding )
  {
    return encoding != null ?
		new CharConverter( encoding )
		: null;
  }


  public boolean equalsEncodingName( String encName )
  {
    boolean rv = false;
    if( encName != null ) {
      encName = encName.toUpperCase();
      switch( this.encoding ) {
	case ASCII:
	  rv = encName.equals( "ASCII" ) || encName.equals( "US-ASCII" );
	  break;
	case ISO646DE:
	  rv = encName.equals( "ISO646DE" ) || encName.equals( "ISO-646DE" );
	  break;
	case CP437:
	  rv = encName.equals( "CP437" );
	  break;
	case CP850:
	  rv = encName.equals( "CP850" );
	  break;
	case ANSI:
	  rv = encName.equals( "ANSI" ) || encName.equals( "CP1252" );
	  break;
	case LATIN1:
	  rv = encName.equals( "LATIN1" ) || encName.equals( "ISO-8859-1" );
	  break;
      }
    }
    return rv;
  }


  public static Encoding getEncodingByName( String encodingName )
  {
    Encoding encoding = null;
    if( encodingName != null ) {
      switch( encodingName.toUpperCase() ) {
	case "ASCII":
	case "US-ASCII":
	  encoding = Encoding.ASCII;
	  break;
	case "ISO646DE":
	case "ISO-646DE":
	  encoding = Encoding.ISO646DE;
	  break;
	case "CP437":
	  encoding = Encoding.CP437;
	  break;
	case "CP850":
	  encoding = Encoding.CP850;
	  break;
	case "ANSI":
	case "CP1252":
	  encoding = Encoding.LATIN1;
	  break;
	case "LATIN1":
	case "ISO-8859-1":
	  encoding = Encoding.LATIN1;
	  break;
      }
    }
    return encoding;
  }


  public static String getEncodingDisplayText( Encoding encoding )
  {
    String rv = "ASCII (keine Umlaute)";
    switch( encoding ) {
      case ISO646DE:
	rv = "Deutsche Variante von ISO-646 (Umlaute anstelle von [\\]{|}~)";
	break;
      case CP437:
	rv = "CP437 (DOS-Zeichensatz f\u00FCr USA)";
	break;
      case CP850:
	rv = "CP850 (DOS-Zeichensatz f\u00FCr Westeuropa)";
	break;
      case ANSI:
	rv = "CP1252 (ANSI, Windows-Zeichensatz)";
	break;
      case LATIN1:
	rv = "ISO-8859-1 (Latin 1)";
	break;
    }
    return rv;
  }


  public String getEncodingName()
  {
    return getEncodingName( this.encoding );
  }


  public static String getEncodingName( Encoding encoding )
  {
    String rv = null;
    if( encoding != null ) {
      switch( encoding ) {
	case ASCII:
	  rv = "ASCII";
	  break;
	case ISO646DE:
	  rv = "ISO646DE";
	  break;
	case CP437:
	  rv = "CP437";
	  break;
	case CP850:
	  rv = "CP850";
	  break;
	case ANSI:
	  rv = "ANSI";
	  break;
	case LATIN1:
	  rv = "LATIN1";
	  break;
      }
    }
    return rv;
  }


  public char toUnicode( int ch )
  {
    char rv = REPLACEMENT_CHAR;
    if( this.encoding == Encoding.ASCII ) {
      if( (ch > 0) && (ch < 0x7F) ) {
	rv = (char) ch;
      }
    }
    else if( encoding == Encoding.ISO646DE ) {
      switch( ch ) {
	case '[':		// Ae
	  rv = '\u00C4';
	  break;
	case '\\':		// Oe
	  rv = '\u00D6';
	  break;
	case ']':		// Ue
	  rv = '\u00DC';
	  break;
	case '{':		// ae
	  rv = '\u00E4';
	  break;
	case '|':		// oe
	  rv = '\u00F6';
	  break;
	case '}':		// ue
	  rv = '\u00FC';
	  break;
	case '~':		// ss
	  rv = '\u00DF';
	  break;
	default:
	  if( (ch > 0) && (ch < 0x7F) ) {
	    rv = (char) ch;
	  }
      }
    }
    else if( encoding == Encoding.CP437 ) {
      if( (ch >= 0) && (ch < cp437ToUnicode.length()) ) {
	rv = cp437ToUnicode.charAt( ch );
      }
    }
    else if( encoding == Encoding.CP850 ) {
      if( (ch >= 0) && (ch < cp850ToUnicode.length()) ) {
	rv = cp850ToUnicode.charAt( ch );
      }
    }
    else if( encoding == Encoding.ANSI ) {
      switch( ch ) {
	case 0x80:
	  rv = '\u20AC';
	  break;
	case 0x82:
	  rv = '\u201A';
	  break;
	case 0x83:
	  rv = '\u0192';
	  break;
	case 0x84:
	  rv = '\u201E';
	  break;
	case 0x85:
	  rv = '\u2026';
	  break;
	case 0x86:
	  rv = '\u2020';
	  break;
	case 0x87:
	  rv = '\u2021';
	  break;
	case 0x88:
	  rv = '\u02C6';
	  break;
	case 0x89:
	  rv = '\u2030';
	  break;
	case 0x8A:
	  rv = '\u0160';
	  break;
	case 0x8B:
	  rv = '\u2039';
	  break;
	case 0x8C:
	  rv = '\u0152';
	  break;
	case 0x8E:
	  rv = '\u017D';
	  break;
	case 0x91:
	  rv = '\u2018';
	  break;
	case 0x92:
	  rv = '\u2019';
	  break;
	case 0x93:
	  rv = '\u201C';
	  break;
	case 0x94:
	  rv = '\u201D';
	  break;
	case 0x95:
	  rv = '\u2022';
	  break;
	case 0x96:
	  rv = '\u2013';
	  break;
	case 0x97:
	  rv = '\u2014';
	  break;
	case 0x98:
	  rv = '\u02DC';
	  break;
	case 0x99:
	  rv = '\u2122';
	  break;
	case 0x9A:
	  rv = '\u0161';
	  break;
	case 0x9B:
	  rv = '\u203A';
	  break;
	case 0x9C:
	  rv = '\u0153';
	  break;
	case 0x9E:
	  rv = '\u017E';
	  break;
	case 0x9F:
	  rv = '\u0178';
	  break;
	default:
	  if( (ch > 0) && (ch <= 0xFF) ) {
	    rv = (char) ch;
	  }
      }
    } else {
      if( (ch > 0) && (ch <= 0xFF) ) {
	rv = (char) ch;
      }
    }
    return rv;
  }


  public int toCharsetByte( char ch )
  {
    int rv = 0;
    if( this.encoding == Encoding.ASCII ) {
      if( (ch > 0) && (ch < 0x7F) ) {
	rv = ch;
      }
    }
    else if( encoding == Encoding.ISO646DE ) {
      if( (ch != '[') && (ch != '\\') && (ch != ']')
	  && (ch != '{') && (ch != '|') && (ch != '}') && (ch != '~') )
      {
	switch( ch ) {
	  case '\u00C4':	// Ae
	    rv = '[';
	    break;
	  case '\u00D6':	// Oe
	    rv = '\\';
	    break;
	  case '\u00DC':	// Ue
	    rv = ']';
	    break;
	  case '\u00E4':	// ae
	    rv = '{';
	    break;
	  case '\u00F6':	// oe
	    rv = '|';
	    break;
	  case '\u00FC':	// ue
	    rv = '}';
	    break;
	  case '\u00DF':	// ss
	    rv = '~';
	    break;
	  default:
	    if( (ch > 0) && (ch < 0x7F) ) {
	      rv = ch;
	    }
	}
      }
    }
    else if( encoding == Encoding.CP437 ) {
      rv = cp437ToUnicode.indexOf( ch );
    }
    else if( encoding == Encoding.CP850 ) {
      rv = cp850ToUnicode.indexOf( ch );
    }
    else if( encoding == Encoding.ANSI ) {
      switch( ch ) {
	case '\u20AC':
	  rv = 0x80;
	  break;
	case '\u201A':
	  rv = 0x82;
	  break;
	case '\u0192':
	  rv = 0x83;
	  break;
	case '\u201E':
	  rv = 0x84;
	  break;
	case '\u2026':
	  rv = 0x85;
	  break;
	case '\u2020':
	  rv = 0x86;
	  break;
	case '\u2021':
	  rv = 0x87;
	  break;
	case '\u02C6':
	  rv = 0x88;
	  break;
	case '\u2030':
	  rv = 0x89;
	  break;
	case '\u0160':
	  rv = 0x8A;
	  break;
	case '\u2039':
	  rv = 0x8B;
	  break;
	case '\u0152':
	  rv = 0x8C;
	  break;
	case '\u017D':
	  rv = 0x8E;
	  break;
	case '\u2018':
	  rv = 0x91;
	  break;
	case '\u2019':
	  rv = 0x92;
	  break;
	case '\u201C':
	  rv = 0x93;
	  break;
	case '\u201D':
	  rv = 0x94;
	  break;
	case '\u2022':
	  rv = 0x95;
	  break;
	case '\u2013':
	  rv = 0x96;
	  break;
	case '\u2014':
	  rv = 0x97;
	  break;
	case '\u02DC':
	  rv = 0x98;
	  break;
	case '\u2122':
	  rv = 0x99;
	  break;
	case '\u0161':
	  rv = 0x9A;
	  break;
	case '\u203A':
	  rv = 0x9B;
	  break;
	case '\u0153':
	  rv = 0x9C;
	  break;
	case '\u017E':
	  rv = 0x9E;
	  break;
	case '\u0178':
	  rv = 0x9F;
	  break;
	default:
	  if( (ch > 0) && (ch <= 0xFF) ) {
	    rv = (char) ch;
	  }
      }
    } else {
      if( (ch > 0) && (ch < 0xFF) ) {
	rv = ch;
      }
    }
    return rv > 0 ? rv : 0;
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public String toString()
  {
    return this.encodingDisplayText;
  }
}
