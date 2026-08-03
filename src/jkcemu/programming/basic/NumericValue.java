/*
 * (c) 2018-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Abbildung einer BASIC-Zahl
 */

package jkcemu.programming.basic;

import java.text.CharacterIterator;
import jkcemu.programming.PrgException;


public class NumericValue
{
  private BasicCompiler.DataType dataType;
  private long                   lValue;
  private long                   valueBits;


  public static NumericValue checkLiteral(
			BasicCompiler          compiler,
			CharacterIterator      iter,
			BasicCompiler.DataType prefDataType,
			boolean                enableWarnings )
							throws PrgException
						
  {
    NumericValue rv = null;
    char         ch = BasicUtil.skipSpaces( iter );
    if( (ch >= '0') && (ch <= '9') ) {
      NumericValue value = readNumber(
				compiler,
				iter,
				prefDataType,
				enableWarnings );
      if( value == null ) {
	BasicUtil.throwNumberExpected();
      }
      rv = value;
    }
    else if( ch == '&' ) {
      ch = iter.next();
      if( (ch == 'B') || (ch == 'b') ) {
	ch = iter.next();
	if( (ch == '0') || (ch == '1') ) {
	  long value = 0L;
	  while( (ch == '0') || (ch == '1') ) {
	    value <<= 1;
	    if( ch == '1' ) {
	      value |= 1;
	    }
	    if( value > 0xFFFFFFFFL ) {
	      BasicUtil.throwNumberTooBig();
	    }
	    ch = iter.next();
	  }
	  rv = fromUnsignedInt( iter, value, prefDataType );
	} else {
	  throw new PrgException( "0 oder 1 erwartet" );
	}
      } else if( (ch == 'H') || (ch == 'h') ) {
	iter.next();
	Number value = BasicUtil.readHex( iter );
	if( value == null ) {
	  BasicUtil.throwHexDigitExpected();
	}
	rv = fromUnsignedInt( iter, value.longValue(), prefDataType );
      } else {
	throw new PrgException( "B oder H erwartet" );
      }
    }
    else if( ch == '\'' ) {
      ch = iter.next();
      iter.next();
      if( enableWarnings
	  && compiler.getBasicOptions().getWarnNonAsciiChars()
	  && ((ch < '\u0020') || (ch > '\u007F')) )
      {
	compiler.putWarningNonAsciiChar( ch );
      }
      BasicUtil.parseToken( iter, '\'' );
      BasicUtil.check8BitChar( ch );
      rv = fromValue( (long) ch, prefDataType );
    }
    return rv;
  }


  public static NumericValue from( int value ) throws PrgException
  {
    return fromValue( (long) value, BasicCompiler.DataType.INT2 );
  }


  public BasicCompiler.DataType getDataType()
  {
    return this.dataType;
  }


  public int intValue()
  {
    return (int) this.lValue;
  }


  public long longValue()
  {
    return this.lValue;
  }


  public NumericValue negate()
  {
    NumericValue rv = this;
    switch( this.dataType ) {
      case INT2:
      case INT4:
	rv = new NumericValue(
			this.dataType,
			-this.lValue,
			-this.valueBits );
	break;
      case FLOAT4:
	rv = new NumericValue(
			this.dataType,
			-this.lValue,
			this.valueBits ^ 0x00800000L );
	break;
      case DEC6:
	rv = new NumericValue(
			this.dataType,
			-this.lValue,
			this.valueBits ^ 0x800000000000L );
    }
    return rv;
  }


  public static NumericValue readNumber(
			BasicCompiler          compiler,
			CharacterIterator      iter,
			BasicCompiler.DataType prefDataType )
							throws PrgException
  {
    return readNumber( compiler, iter, prefDataType, true );
  }


  public long valueBits()
  {
    return this.valueBits;
  }


  public void writeCode_LD_Reg_DirectValue( BasicCompiler compiler )
  {
    AsmCodeBuf asmOut = compiler.getCodeBuf();
    switch( this.dataType ) {
      case INT2:
	asmOut.append_LD_HL_nn( (int) this.lValue );
	break;
      case INT4:
	asmOut.append_LD_DEHL_nnnn( this.lValue );
	break;
      case FLOAT4:
	asmOut.append_LD_DEHL_nnnn( this.valueBits );
	break;
      case DEC6:
	asmOut.append( "\tCALL\tD6_LD_ACCU_NNNNNN\n"
			+ "\tDB\t" );
	long d6Bits = this.valueBits;
	for( int i = 0; i < 6; i++ ) {
	  if( i > 0 ) {
	    asmOut.append( ',' );
	  }
	  asmOut.appendHex2( (int) (d6Bits >> 40) );
	  d6Bits <<= 8;
	}
	asmOut.newLine();
	compiler.addLibItem( BasicLibrary.LibItem.D6_LD_ACCU_NNNNNN );
	break;
    }
  }


	/* --- private Methoden --- */

  private static NumericValue fromValue(
				long                   value,
				BasicCompiler.DataType prefDataType )
					throws PrgException
  {
    BasicCompiler.DataType dataType  = null;
    long                   valueBits = value;
    if( prefDataType != null ) {
      if( prefDataType.equals( BasicCompiler.DataType.INT2 )
	  && isInt2( value ) )
      {
	dataType = BasicCompiler.DataType.INT2;
      }
      else if( prefDataType.equals( BasicCompiler.DataType.INT4 ) ) {
	dataType = BasicCompiler.DataType.INT4;
      }
      else if( prefDataType.equals( BasicCompiler.DataType.FLOAT4 ) ) {
	dataType  = BasicCompiler.DataType.FLOAT4;
	valueBits = floatToF4Bits( (float) value );
      }
      else if( prefDataType.equals( BasicCompiler.DataType.DEC6 ) ) {
	long d6Bits = longToD6Bits( value );
	if( d6Bits != -1 ) {
	  dataType  = BasicCompiler.DataType.DEC6;
	  valueBits = d6Bits;
	}
      }
    }
    if( dataType == null ) {
      if( isInt2( value ) ) {
	dataType = BasicCompiler.DataType.INT2;
      } else if( isInt4( value ) ) {
	dataType = BasicCompiler.DataType.INT4;
      } else {
	long d6Bits = longToD6Bits( value );
	if( d6Bits == -1 ) {
	  BasicUtil.throwNumberTooBig();
	}
	dataType  = BasicCompiler.DataType.FLOAT4;
	valueBits = d6Bits;
      }
    }
    return new NumericValue( dataType, value, valueBits );
  }


  private static NumericValue readNumber(
			BasicCompiler          compiler,
			CharacterIterator      iter,
			BasicCompiler.DataType prefDataType,
			boolean                enableWarnings )
							throws PrgException
  {
    boolean       exp            = false;
    boolean       d6Valid        = false;
    boolean       d6Overflow     = false;
    boolean       f4Valid        = false;
    boolean       i4Valid        = false;
    boolean       i4Overflow     = false;
    boolean       hasDigits      = false;
    int           begPos         = iter.getIndex();
    int           prec           = 0;
    int           scale          = 0;
    int           d6FracsIgnored = 0;
    long          d6Bits         = 0;
    long          i4Value        = 0;
    Float         f4Value        = null;
    StringBuilder chBuf          = new StringBuilder();
    char          ch             = BasicUtil.skipSpaces( iter );
    while( ch == '0' ) {
      hasDigits = true;
      d6Valid   = true;
      f4Valid   = true;
      i4Valid   = true;
      if( chBuf.length() == 0 ) {
	chBuf.append( ch );
      }
      ch = iter.next();
    }
    if( (ch >= '0') && (ch <= '9') ) {
      chBuf.append( ch );
      prec++;
      hasDigits = true;
      d6Valid   = true;
      f4Valid   = true;
      i4Valid   = true;
      i4Value   = ch - '0';
      d6Bits    = i4Value;
      ch        = iter.next();
      while( (ch >= '0') && (ch <= '9') ) {
	chBuf.append( ch );
	prec++;
	int cValue = ch - '0';
	if( !d6Overflow ) {
	  d6Bits = (d6Bits << 4) | cValue;
	  if( (d6Bits & 0xFFFFF00000000000L) != 0 ) {
	    d6Overflow = true;
	  }
	}
	if( !i4Overflow ) {
	  i4Value = (i4Value * 10) + cValue;
	  if( (i4Value & 0xFFFFFFFF80000000L) != 0 ) {
	    i4Overflow = true;
	  }
	}
	ch = iter.next();
      }
    }
    if( ch == '.' ) {
      d6Valid = false;
      f4Valid = false;
      i4Valid = false;
      chBuf.append( ch );
      ch = iter.next();
      if( (ch >= '0') && (ch <= '9') ) {
	d6Valid = true;
	f4Valid = true;
	while( (ch >= '0') && (ch <= '9') ) {
	  chBuf.append( ch );
	  if( (prec < 11) && (scale < 7) ) {
	    d6Bits = (d6Bits << 4) | (ch - '0');
	    prec++;
	    scale++;
	  } else {
	    d6FracsIgnored++;
	  }
	  ch = iter.next();
	}
	while( (scale > 0) && ((d6Bits & 0x0F) == 0) ) {
	  d6Bits >>= 4;
	  --scale;
	}
	d6Bits |= ((long) scale << 44);
      }
      if( ch == '.' ) {
	d6Valid = false;
	f4Valid = false;
      }
    }
    if( f4Valid ) {
      int tmpPos = iter.getIndex();
      int tmpLen = chBuf.length();
      try {
	if( (ch == 'E') || (ch == 'e') ) {
	  chBuf.append( ch );
	  d6Valid = false;
	  f4Valid = false;
	  i4Valid = false;
	  exp     = true;
	  ch      = iter.next();
	}
	if( exp ) {
	  if( (ch == '+') || (ch == '-') ) {
	    chBuf.append( ch );
	    f4Valid = false;
	    ch      = iter.next();
	  }
	  while( (ch >= '0') && (ch <= '9') ) {
	    chBuf.append( ch );
	    f4Valid = true;
	    ch      = iter.next();
	  }
	}
	if( !f4Valid ) {
	  iter.setIndex( tmpPos );
	  chBuf.setLength( tmpLen );
	}
	f4Value = Float.parseFloat( chBuf.toString() );
	tmpPos  = iter.getIndex();
      }
      catch( NumberFormatException ex ) {}
      finally {
	iter.setIndex( tmpPos );
      }
    }
    NumericValue rv = null;
    if( hasDigits ) {
      if( (ch == 'D') || (ch == 'd') ) {
	ch = iter.next();
	if( !d6Valid ) {
	  throw new PrgException( "Ung\u00FCltige Decimal-Zahl" );
	}
	if( d6Overflow ) {
	  BasicUtil.throwNumberTooBig();
	}
	rv = new NumericValue(
			BasicCompiler.DataType.DEC6,
			i4Value,
			d6Bits );
      } else if( (ch == 'F') || (ch == 'f') ) {
	ch = iter.next();
	if( f4Value == null ) {
	  throw new PrgException( "Ung\u00FCltige Single-Zahl" );
	}
	if( f4Value.isInfinite() || f4Value.isNaN() ) {
	  BasicUtil.throwNumberTooBig();
	}
	rv = new NumericValue(
			BasicCompiler.DataType.FLOAT4,
			i4Value,
			floatToF4Bits( f4Value ) );
      } else if( i4Valid && ((ch == 'L') || (ch == 'l')) ) {
	ch = iter.next();
	if( !i4Valid ) {
	  throw new PrgException( "Ung\u00FCltige Long-Zahl" );
	}
	if( i4Overflow ) {
	  BasicUtil.throwNumberTooBig();
	}
	rv = new NumericValue(
			BasicCompiler.DataType.INT4,
			i4Value,
			i4Value );
      }
    }
    if( rv == null ) {
      if( prefDataType != null ) {
	if( prefDataType.equals( BasicCompiler.DataType.INT2 )
	    && i4Valid && !i4Overflow && isInt2( i4Value ) )
	{
	  rv = new NumericValue(
			BasicCompiler.DataType.INT2,
			i4Value,
			i4Value );
	}
	else if( prefDataType.equals( BasicCompiler.DataType.INT4 )
		 && i4Valid && !i4Overflow )
	{
	  rv = new NumericValue(
			BasicCompiler.DataType.INT4,
			i4Value,
			i4Value );
	}
	else if( prefDataType.equals( BasicCompiler.DataType.DEC6 )
		 && d6Valid && !d6Overflow )
	{
	  rv = new NumericValue(
			BasicCompiler.DataType.DEC6,
			i4Value,
			d6Bits );
	}
	else if( prefDataType.equals( BasicCompiler.DataType.FLOAT4 )
		 && (f4Value != null) )
	{
	  if( f4Value.isInfinite() || f4Value.isNaN() ) {
	    BasicUtil.throwNumberTooBig();
	  }
	  rv = new NumericValue(
			BasicCompiler.DataType.FLOAT4,
			i4Value,
			floatToF4Bits( f4Value ) );
	}
      }
    }
    if( rv == null ) {
      if( i4Valid && !i4Overflow && isInt2( i4Value ) ) {
	rv = new NumericValue(
			BasicCompiler.DataType.INT2,
			i4Value,
			i4Value );
      } else if( i4Valid && !i4Overflow ) {
	rv = new NumericValue(
			BasicCompiler.DataType.INT4,
			i4Value,
			i4Value );
      // Float4 dem Dec6 vorziehen
      } else if( f4Value != null ) {
	if( f4Value.isInfinite() || f4Value.isNaN() ) {
	  BasicUtil.throwNumberTooBig();
	}
	rv = new NumericValue(
			BasicCompiler.DataType.FLOAT4,
			i4Value,
			floatToF4Bits( f4Value ) );
      } else if( d6Valid && !d6Overflow ) {
	rv = new NumericValue(
			BasicCompiler.DataType.DEC6,
			i4Value,
			d6Bits );
      }
    }
    if( (rv == null)
	&& ((d6Valid && d6Overflow) || (i4Valid && i4Overflow)) )
    {
      BasicUtil.throwNumberTooBig();
    }
    if( (rv == null) && hasDigits ) {
      throw new PrgException( "Ung\u00FCltige Zahl" );
    }
    if( compiler.getBasicOptions().getWarnTooManyDigits()
	&& (rv != null) )
    {
      if( (rv.getDataType() == BasicCompiler.DataType.DEC6)
	  && (d6FracsIgnored > 0) )
      {
	compiler.putWarningLastDigitsIgnored( d6FracsIgnored );
      }
    }
    return rv;
  }


	/* --- Konstruktor --- */

  private NumericValue(
		BasicCompiler.DataType dataType,
		long                   lValue,
		long                   valueBits )
  {
    this.dataType  = dataType;
    this.lValue    = lValue;
    this.valueBits = valueBits;
  }


  private static long floatToF4Bits( float value )
  {
    long m = Float.floatToIntBits( value );
    int  e = (int) ((m >> 23) & 0xFF) - 0x7F + FloatLibrary.F4_BIAS;
    return ((m >> 8) & 0x00800000L)
		| ((e << 24) & 0xFF000000L)
		| (m & 0x007FFFFFL);
  }


  private static NumericValue fromUnsignedInt(
				CharacterIterator      iter,
				long                   value,
				BasicCompiler.DataType prefDataType )
							throws PrgException
  {
    if( value > 0xFFFFFFFFL ) {
      BasicUtil.throwNumberTooBig();
    }
    NumericValue rv = null;
    char         ch = iter.current();
    if( (ch == 'L') || (ch == 'l') ) {
      iter.next();
      rv = new NumericValue(
			BasicCompiler.DataType.INT4,
			value,
			value );
    } else if( prefDataType == null ) {
      if( value > 0xFFFF ) {
	rv = new NumericValue(
			BasicCompiler.DataType.INT4,
			value,
			value );
      } else {
	if( (value & 0x8000) != 0 ) {
	  value |= 0xFFFF0000L;
	}
	rv = new NumericValue(
			BasicCompiler.DataType.INT2,
			value,
			value );
      }
    }
    return rv != null ? rv : fromValue( value, prefDataType );
  }


  private static boolean isInt2( long lValue )
  {
    return (lValue >= -0x7FFFL) && (lValue <= 0x7FFFL);
  }


  private static boolean isInt4( long lValue )
  {
    return (lValue >= -0x7FFFFFFFL) && (lValue <= 0x7FFFFFFFL);
  }


  private static long longToD6Bits( long value )
  {
    long d6Bits = -1L;
    if( (value >= -99999999999L) && (value <= 99999999999L) ) {
      boolean neg = false;
      long    v   = value;
      if( v < 0 ) {
	neg = true;
	v -= value;
      }
      d6Bits = 0;
      for( int i = 0; i < 11; i++ ) {
	d6Bits = (((v % 10) << 40) & 0x0F0000000000L) | (d6Bits >> 4);
	v /= 10;
      }
      if( neg ) {
	d6Bits |= 0x800000000000L;
      }
    }
    return d6Bits;
  }
}
