/*
 * (c) 2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Funktionen zum Suchen und Auswerten eines CPM-kompatiblen Directorys
 */

package jkcemu.disk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jkcemu.base.EmuUtil;
import jkcemu.file.FileEntry;
import jkcemu.file.FileUtil;


public class CPMDirUtil
{
  public enum DirStatus { NO_DIR, EMPTY_DIR, FILLED_DIR };


  private static final int MAX_FIND_DIR_CYLINDERS   = 4;
  private static final int VIRTUAL_SECTOR_SIZE_CODE = 2;

  /*
   * Die Konstante gibt an, wieviel Bytes zum Suchen des Directorys
   * von einer einfachen Diskettenabbilddatei gelesen werden.
   * Der Wert 147456 entspricht der Groesse von 4 Spuren
   * (max. drei Systemspuren und eine Directory-Spur)
   * einer 2.88 MByte Diskette.
   */
  private static final int PLAINDISK_MAX_SEARCH_DIR_LEN = 147456;


  public static DirStatus checkDirStatus(
				byte[] dirBytes,
				int    offs,
				int    len )
  {
    int nEmpty   = 0;
    int nFilled  = 0;
    int nInvalid = 0;
    if( dirBytes != null ) {
      int entryPos = offs;
      int endPos   = Math.min( offs + len, dirBytes.length );
      while( (entryPos + 31) < endPos ) {
	if( dirBytes[ entryPos ] == (byte) 0xE5 ) {
	  nEmpty++;
	} else if( isValidFilledDirEntry(
				dirBytes,
				entryPos,
				false,
				null,
				null ) )
	{
	  nFilled++;
	} else {
	  nInvalid++;
	}
	entryPos += 32;
      }
    }
    DirStatus rv = DirStatus.NO_DIR;
    if( (nFilled + nEmpty) > (10 * nInvalid) ) {
      rv = (nFilled > 0 ? DirStatus.FILLED_DIR : DirStatus.EMPTY_DIR );
    }
    return rv;
  }


  public static List<FileEntry> extractDir(
					byte[]  dirBytes,
					boolean withDeleted )
  {
    List<FileEntry> rv = null;
    if( dirBytes != null ) {
      if( dirBytes.length > 0 ) {
	Map<String,Long> fileKeyToSize = new HashMap<>();
	Set<String>      fileNames     = new HashSet<>();
	StringBuilder    fileNameBuf   = new StringBuilder();
	AtomicInteger    userNumBuf    = new AtomicInteger();
	int              entryPos      = 0;
	while( (entryPos + 15) < dirBytes.length ) {
	  fileNameBuf.setLength( 0 );
	  if( isValidFilledDirEntry(
				dirBytes,
				entryPos,
				withDeleted,
				userNumBuf,
				fileNameBuf ) )
	  {
	    String fileName = fileNameBuf.toString();
	    if( !fileName.isEmpty() ) {
	      String  fileKey = "";
	      int     userNum = userNumBuf.get();
	      boolean deleted = (userNum == 0xE5);
	      if( deleted ) {
		fileKey = "gel\u00F6scht: " + fileName;
	      } else if( userNum > 0 ) {
		fileKey = String.format( "%d: %s", userNum, fileName );
	      } else {
		fileKey = fileName;
	      }
	      if( !fileKeyToSize.containsKey( fileKey )
		  && (!deleted || !fileNames.contains( fileName )) )
	      {
		fileKeyToSize.put(
				fileKey,
				getFileSize( dirBytes, entryPos ) );
	      }
	      fileNames.add( fileName );
	    }
	  }
	  entryPos += 32;
	}
	Set<String> fileKeys = fileKeyToSize.keySet();
	if( fileKeys != null ) {
	  int n = fileKeys.size();
	  if( n > 0 ) {
	    String[] a = fileKeys.toArray( new String[ n ] );
	    if( a != null ) {
	      Arrays.sort( a );
	      rv = new ArrayList<>( n );
	      for( int i = 0; i < a.length; i++ ) {
		String fileKey  = a[ i ];
		if( fileKey != null ) {
		  String fileName = fileKey;
		  if( fileKey.startsWith( "0: " ) ) {
		    fileName = fileKey.substring( 3 );
		  }
		  if( !fileName.isEmpty() ) {
		    FileEntry entry = new FileEntry( fileName );
		    entry.setSize( fileKeyToSize.get( fileKey ) );
		    rv.add( entry );
		  }
		}
	      }
	    }
	  }
	}
	if( rv == null ) {
	  // leere aber gueltige Abbilddatei
	  rv = new ArrayList<>();
	}
      }
    }
    return rv;
  }


  public static List<FileEntry> extractDirFromPlainDiskFile( File file )
  {
    List<FileEntry> entries = null;
    try {
      entries = extractDir(
			findAndReadDirBytes(
				FileUtil.readFile(
					file,
					true,
					PLAINDISK_MAX_SEARCH_DIR_LEN ),
				null,
				null,
				null ),
			false );
    }
    catch( IOException ex ) {}
    return entries;
  }


  /*
   * Die Methode sucht und liest das Directory
   * von einem AbstractDisk-Objekt.
   * Das Directory muss am Anfang einer Spur auf der ersten Seite beginnen.
   * Ausserdem mussen alle Directory-Sektoren die gleiche Groesse haben
   * und die Sektornummern muessen lueckenlos aufsteigend sein.
   *
   * Optional kann man sich die Sektoren des Directorys
   * und des Datenbereichs sortiert zurueckgeben lassen.
   * Diese List endet an der Stelle,
   * wo die Sektoranordnung unregelmaessig wird.
   *
   * Rueckgabe:
   *   Directory-Bytes oder leeres Array, wenn Director leer ist
   *   null, wenn kein Director gefunden wurde
   */
  public static byte[] findAndReadDirBytes(
				AbstractFloppyDisk disk,
				List<SectorData>   rvDataSectors,
				StringBuilder      rvDataTruncatedReason )
							throws IOException
  {
    // ersten gefuellten Directory-Sektor am Spuranfang suchen
    boolean emptyDirSectors = false;
    int     cyls            = disk.getCylinders();
    int     maxDirCyls      = Math.min( cyls, MAX_FIND_DIR_CYLINDERS );
    int     firstDirCyl     = -1;
    int     firstSectorNum  = -1;
    int     endSectorNum    = -1;
    int     sizeCode        = -1;
    for( int cyl = 0; cyl < maxDirCyls; cyl++ ) {
      SortedSet<SectorData> sectors = disk.getSortedTrackSectors( cyl, 0 );
      int nSectors = sectors.size();
      if( nSectors > 0 ) {
	try {
	  SectorData sector = sectors.first();
	  if( (sector.getCylinder() == cyl)
	      && (sector.getHead() == 0) )
	  {
	    DirStatus dirStatus = checkDirStatus(
					sector.getDataBytes(),
					0,
					sector.getDataLength() );
	    if( dirStatus.equals( DirStatus.EMPTY_DIR ) ) {
	      emptyDirSectors = true;
	    } else if( dirStatus.equals( DirStatus.FILLED_DIR ) ) {
	      firstDirCyl    = cyl;
	      firstSectorNum = sector.getSectorNum();
	      endSectorNum   = firstSectorNum + nSectors;
	      sizeCode       = sector.getSizeCode();
	      break;
	    }
	  }
	}
	catch( NoSuchElementException ex ) {
	  break;
	}
      }
      if( firstDirCyl >= 0 ) {
	break;
      }
    }

    // Sektoren lesen
    byte[] dirBytes = null;
    if( firstDirCyl >= 0 ) {
      ByteArrayOutputStream dirBuf = new ByteArrayOutputStream( 0x2000 );

      boolean cancelled = false;
      boolean insideDir = true;
      int     sides     = disk.getSides();
      for( int cyl = firstDirCyl; !cancelled && (cyl < cyls); cyl++ ) {
	for( int head = 0; !cancelled && (head < sides); head++ ) {
	  SortedSet<SectorData> sectors = disk.getSortedTrackSectors(
								cyl,
								head );
	  int sectorNum = firstSectorNum;
	  for( SectorData sector : sectors ) {
	    if( (sector.getCylinder() != cyl)
		|| (sector.getHead() != head)
		|| (sector.getSectorNum() != sectorNum)
		|| (sector.getSizeCode() != sizeCode) )
	    {
	      // ab hier unregelmaessige Sektoranordnung
	      cancelled = true;
	      break;
	    }
	    if( rvDataSectors != null ) {
	      rvDataSectors.add( sector );
	    }
	    if( insideDir
		&& (checkDirStatus(
			sector.getDataBytes(),
			0,
			sector.getDataLength() ) == DirStatus.NO_DIR) )
	    {
	      insideDir = false;
	    }
	    if( insideDir ) {
	      sector.writeTo( dirBuf, sector.getDataLength() );
	    } else {
	      if( rvDataSectors == null ) {
		// kein weiteres Lesen von Sektoren notwendig
		cancelled = true;
	      }
	    }
	    if( cancelled ) {
	      break;
	    }
	    sectorNum++;
	  }
	  if( sectorNum != endSectorNum ) {
	    // ab hier unregelmaessige Sektoranordnung
	    cancelled = true;
	    if( rvDataTruncatedReason != null ) {
	      if( sectorNum < endSectorNum ) {
		rvDataTruncatedReason.append(
			String.format(
				"Spur %d, Seite %d:"
					+ " Sektor [C=%d,H=%d,R=%d,N=%d]"
					+ " nicht gefunden",
				cyl,
				head + 1,
				cyl,
				head,
				sectorNum,
				sizeCode ) );
	      } else {
		rvDataTruncatedReason.append(
			String.format(
				"Spur %d, Seite %d: Unerwarteter Sektor"
					+ " %d gefunden",
				cyl,
				head + 1,
				sectorNum ) );
	      }
	    }
	  }
	}
      }
      dirBytes = dirBuf.toByteArray();
    }

    // Directory-Bytes zurueckgeben
    if( (dirBytes == null) && emptyDirSectors ) {
      dirBytes = new byte[ 0 ];
    }
    return dirBytes;
  }


  /*
   * Die Methode sucht und liest das Directory
   * von einem Byte-Array, welches die Daten einer einfachen
   * Diskettenabbilddatei enthaelt.
   * Dabei wird wird das Byte-Array in virtuelle Sektoren aufgeteilt.
   *
   * Optional kann man sich die virtuellen Sektoren des Directorys
   * und des Datenbereichs sortiert zurueckgeben lassen.
   *
   * Rueckgabe:
   *   Directory-Bytes oder leeres Array, wenn Directory leer ist
   *   null, wenn kein Directory gefunden wurde
   */
  public static byte[] findAndReadDirBytes(
				byte[]           plainDiskBytes,
				AtomicInteger    rvSysOffs,
				AtomicInteger    rvDirOffs,
				List<SectorData> rvDataSectors )
  {
    int virtualSectorSize = 0x80;
    if( VIRTUAL_SECTOR_SIZE_CODE > 0 ) {
      virtualSectorSize <<= VIRTUAL_SECTOR_SIZE_CODE;
    }

    /*
     * Wenn die Daten groesser als eine ED-Diskette (2.88 MB)
     * mit mehr als 82 Spuren (2,952 MB) sind,
     * muessen sie von einer Festplatte stammen.
     * Festplattenabbilddateien koennen einen 256-Byte grossen
     * Kopfblock enthalten, der ubergangen werden muss.
     * Wenn die Laenge des Byte-Arrays durch 256, nicht aber durch 512
     * teilbar ist, wird von so einem Kopfblock ausgegangen
     * und diese wird uebersprungen.
     */
    int sysOffs = 0;
    if( ((plainDiskBytes.length % 256) == 0)
	&& ((plainDiskBytes.length % 512) != 0) )
    {
      sysOffs = 256;
    }
    if( rvSysOffs != null ) {
      rvSysOffs.set( sysOffs );
    }

    // ersten gefuellten virtuellen Directory-Sektor suchen
    boolean emptyDirSectors = false;
    int     filledDirPos    = -1;
    int     pos             = sysOffs;
    int     maxSearchLen    = plainDiskBytes.length;
    if( maxSearchLen > (sysOffs + PLAINDISK_MAX_SEARCH_DIR_LEN) ) {
      maxSearchLen = (sysOffs + PLAINDISK_MAX_SEARCH_DIR_LEN);
    }
    while( (pos + virtualSectorSize) <= maxSearchLen ) {
      DirStatus dirStatus = checkDirStatus(
					plainDiskBytes,
					pos,
					virtualSectorSize );
      if( dirStatus.equals( DirStatus.EMPTY_DIR ) ) {
	emptyDirSectors = true;
      } else if( dirStatus.equals( DirStatus.FILLED_DIR ) ) {
	filledDirPos = pos;
	break;
      }
      pos += virtualSectorSize;
    }

    // virtuelle Sektoren lesen
    byte[] dirBytes = null;
    if( filledDirPos >= 0 ) {
      if( rvDirOffs != null ) {
	rvDirOffs.set( filledDirPos );
      }
      pos = filledDirPos;
      while( (pos + virtualSectorSize) < plainDiskBytes.length ) {
	if( checkDirStatus(
			plainDiskBytes,
			pos,
			virtualSectorSize ).equals( DirStatus.NO_DIR ) )
	{
	  if( pos > filledDirPos ) {
	    dirBytes = new byte[ pos - filledDirPos ];
	    System.arraycopy(
			plainDiskBytes,
			filledDirPos,
			dirBytes,
			0,
			dirBytes.length );
	  }
	  break;
	}
	pos += virtualSectorSize;
      }
      if( rvDataSectors != null ) {
	int sectorIdx = 0;
	pos           = filledDirPos;
	while( (pos + virtualSectorSize) < plainDiskBytes.length ) {
	  rvDataSectors.add(
			new SectorData(
				sectorIdx,
				0,
				0,
				sectorIdx + 1,
				VIRTUAL_SECTOR_SIZE_CODE,
				plainDiskBytes,
				pos,
				virtualSectorSize ) );
	  sectorIdx++;
	  pos += virtualSectorSize;
	}
      }
    }

    // Directory-Bytes zurueckgeben
    if( (dirBytes == null) && emptyDirSectors ) {
      dirBytes = new byte[ 0 ];
    }
    return dirBytes;
  }


  public static int findNextExtentPos(
				byte[] dirBytes,
				int    extentPos )
  {
    int rv = -1;
    if( (extentPos + 31) < dirBytes.length ) {
      int pos = extentPos + 32;
      while( (pos + 31) < dirBytes.length ) {
	boolean found = dirBytes[ extentPos ] == dirBytes[ pos ];
	for( int i = 1; i <= 11; i++ ) {
	  if( ((int) dirBytes[ extentPos + i ] & 0x7F)
		    != ((int) dirBytes[ pos + i ] & 0x7F) )
	  {
	    found = false;
	    break;
	  }
	}
	if( found ) {
	  rv = pos;
	  break;
	}
	pos += 32;
      }
    }
    return rv;
  }


  public static int getExtentNumByEntryPos( byte[] dirBytes, int entryPos )
  {
    int rv = 0;
    if( dirBytes != null ) {
      if( (entryPos + 14) < dirBytes.length ) {
	rv = ((int) dirBytes[ entryPos + 12 ] & 0x1F)
		| (((int) dirBytes[ entryPos + 14 ] << 5) & 0x07E0);
      }
    }
    return rv;
  }


  /*
   * Extents pro Directory-Eintrag ermitteln
   *
   * Da pro Extent die Groesse in 80h-Bloecken max. 4000h sein kann,
   * muessen u.U. pro Directory-Eintrag mehrere Extents verwendet werden.
   */
  public static int getExtentsPerDirEntry(
				int     blockSize,
				boolean blockNum16Bit )
  {
    int sizePerDirEntry = blockSize * (blockNum16Bit ? 8 : 16);
    return sizePerDirEntry > 0x4000 ?
		((sizePerDirEntry + 0x4000 - 1) / 0x4000) : 1;
  }


  public static boolean isValidFileNameChar( char ch )
  {
    boolean rv = false;
    if( (ch > '\u0020') && (ch <= '\u007E') ) {
      if( "<>.,;:=?*[]".indexOf( ch ) < 0 ) {
	rv = true;
      }
    }
    return rv;
  }


  public static boolean isValidFilledDirEntry(
					byte[]        dirBytes,
					int           offs,
					boolean       allowDeletedEntry,
					AtomicInteger rvUserNum,
					StringBuilder rvFileName )
  {
    boolean rv = false;
    if( dirBytes != null ) {
      if( (offs + 31) < dirBytes.length ) {
	int     b    = dirBytes[ offs ] & 0xFF;
	boolean isE5 = (b == 0xE5);
	if( ((b & 0xEF) < 0x20)		// User-Nummer ohne Passwort-Bit
	    || (isE5 && allowDeletedEntry) )
	{
	  int userNum = b & 0xEF;
	  if( isE5 ) {
	    userNum = 0xE5;
	  }
	  rv = true;

	  // Dateiname pruefen
	  char[] nameBuf = new char[ 11 ];
	  Arrays.fill( nameBuf, '\u0000' );
	  int pos = offs + 1;
	  for( int i = 0; i < nameBuf.length; i++ ) {
	    b = (int) dirBytes[ pos++ ] & 0xFF;
	    if( b != 0xE5 ) {
	      isE5 = false;
	    }
	    b &= 0x7F;
	    if( b == 0x20 ) {
	      nameBuf[ i ] = '\u0020';
	    } else if( (b > 0x20) && (b < 0x7F) ) {
	      if( !isValidFileNameChar( (char) b ) ) {
		rv = false;
		break;
	      }
	      nameBuf[ i ] = (char) b;
	    } else {
	      rv = false;
	      break;
	    }
	  }
	  String baseName = trimRight( nameBuf, 0, 8 );
	  if( baseName.isEmpty() ) {
	    rv = false;
	  }
	  String extName = trimRight( nameBuf, 8, 11 );

	  // Blocknummern pruefen
	  if( rv ) {
	    boolean blockNums8   = true;
	    boolean blockNums16  = true;
	    int     lastBlockNum = 0;
	    pos                  = offs + 16;
	    for( int i = 0; i < 16; i++ ) {
	      int blockNum = (int) dirBytes[ pos++ ] & 0xFF;
	      if( i > 0 ) {
		if( (blockNum > 0)
		    && ((lastBlockNum == 0) || (blockNum <= lastBlockNum)) )
		{
		  blockNums8 = false;
		  break;
		}
	      } else {
		lastBlockNum = blockNum;
	      }
	    }
	    lastBlockNum = 0;
	    pos          = offs + 16;
	    for( int i = 0; i < 8; i++ ) {
	      int blockNum = EmuUtil.getWord( dirBytes, pos );
	      if( i > 0 ) {
		if( (blockNum > 0)
		    && ((lastBlockNum == 0) || (blockNum <= lastBlockNum)) )
		{
		  blockNums16 = false;
		  break;
		}
	      } else {
		lastBlockNum = blockNum;
	      }
	      pos += 2;
	    }
	    if( !blockNums8 && !blockNums16 ) {
	      rv = false;
	    }
	  }
	  if( rv ) {

	    // User-Nummer zurueckgeben
	    if( rvUserNum != null ) {
	      rvUserNum.set( userNum );
	    }

	    // Dateiname zurueckgeben
	    if( rvFileName != null ) {
	      rvFileName.append( baseName );
	      if( !extName.isEmpty() ) {
		rvFileName.append( '.' );
		rvFileName.append( extName );
	      }
	    }
	  }
	}
      }
    }
    return rv;
  }


  /*
   * Die Methode ermittelt das BlockNummernformat und die Blockgroesse
   *
   * Rueckgabe:
   *   true:  Werte in blockNumSizeOut und blockSizeOut gesetzt
   *   false: Werte konnten nicht ermitteln werden
   */
  public static boolean recognizeBlockNumFmt(
				byte[]        dirBytes,
				AtomicInteger blockNumSizeOut,
				AtomicInteger blockSizeOut,
				AtomicBoolean blockSizeUniqueOut )
  {
    boolean rv = false;
    if( dirBytes != null ) {
      AtomicInteger      userNumBuf     = new AtomicInteger();
      StringBuilder      fileNameBuf    = new StringBuilder();
      Set<String>        fileKeys       = new HashSet<>();
      SortedSet<Integer> block8Sizes    = new TreeSet<>();
      SortedSet<Integer> block16Sizes   = new TreeSet<>();
      int                minBlock8Size  = 0;
      int                minBlock16Size = 0;
      int                minBlock8Num   = 0;
      int                minBlock16Num  = 0;
      int                entryPos       = 0;
      while( (entryPos + 31) < dirBytes.length ) {
	fileNameBuf.setLength( 0 );
	if( isValidFilledDirEntry(
				dirBytes,
				entryPos,
				true,
				userNumBuf,
				fileNameBuf ) )
	{
	  if( fileKeys.add(
			String.format(
				"%02X:%s",
				userNumBuf.get(),
				fileNameBuf.toString() ) ) )
	  {
	    // 8- und 16-Bit Blocknummern des Eintrags lesen
	    Set<Integer> block16Nums = new TreeSet<>();
	    Set<Integer> block8Nums  = null;
	    if( block8Sizes != null ) {
	      block8Nums = new TreeSet<>();
	    }
	    int pos = entryPos + 16;
	    for( int i = 0; i < 8; i++ ) {
	      int blockNum = EmuUtil.getWord( dirBytes, pos );
	      if( blockNum > 0 ) {
		block16Nums.add( blockNum );
		if( (minBlock16Num < 1) || (minBlock16Num > blockNum) ) {
		  minBlock16Num = blockNum;
		}
	      }
	      pos += 2;
	    }
	    if( block8Nums != null ) {
	      pos = entryPos + 16;
	      for( int i = 0; i < 16; i++ ) {
		int blockNum = (int) dirBytes[ pos++ ] & 0xFF;
		if( blockNum == 0xE5 ) {
		  block8Nums = null;	// Eintrag ignorieren
		  break;
		}
		if( blockNum > 0 ) {
		  if( !block8Nums.add( blockNum ) ) {
		    /*
		     * Zwei gleiche Blocknummern kann nicht sein
		     * -> keine 8-Bit-Blocknummern
		     */
		    block8Sizes = null;
		  }
		  if( (minBlock8Num < 1) || (minBlock8Num > blockNum) ) {
		    minBlock8Num = blockNum;
		  }
		}
	      }
	      if( block8Sizes != null ) {
		Set<Integer> hiBytes = new TreeSet<>();
		for( Integer tmpNum : block16Nums ) {
		  int hiByte = tmpNum.intValue() >> 8;
		  if( (hiByte != 0xE5) && !hiBytes.add( hiByte ) ) {
		    /*
		     * Wenn das obere Byte gleich ist,
		     * muessen es 16-Bit Blocknummern sein.
		     * -> keine 8-Bit-Blocknummern
		     */
		    block8Sizes = null;
		    break;
		  }
		}
	      }
	    }

	    /*
	     * Blockgroesse anhand der Groesse und der Blockanzahl
	     * des physischen Extents ermitteln
	     */
	    int entrySize = ((int) dirBytes[ entryPos + 15 ] & 0xFF) * 0x80;
	    if( entrySize > 0 ) {
	      entrySize += (16 * 1024 * getExtentNumByEntryPos(
							dirBytes,
							entryPos ));
	      int blockSize = 0;
	      int blockCnt  = 0;
	      if( (block8Sizes != null) && (block8Nums != null) ) {
		blockCnt = block8Nums.size();
		if( blockCnt > 0 ) {
		  blockSize = getBlockSize( entrySize, blockCnt );
		  if( blockCnt > 1 ) {
		    block8Sizes.add( blockSize );
		  } else if( blockSize > minBlock8Size ) {
		    minBlock8Size = blockSize;
		  }
		}
	      }
	      blockCnt = block16Nums.size();
	      if( blockCnt > 0 ) {
		blockSize = getBlockSize( entrySize, blockCnt );
		if( blockCnt > 1 ) {
		  block16Sizes.add( blockSize );
		} else if( blockSize > minBlock16Size ) {
		  minBlock16Size = blockSize;
		}
	      }
	    }
	  }
	}
	entryPos += 32;
      }

      /*
       * Blockgroesse anhand der Directory-Groesse
       * und der kleinsten verwendeten Blocknummer ermitteln
       */
      if( (block8Sizes != null) && (minBlock8Num > 0) ) {
	if( block8Sizes.size() != 1 ) {
	  int blockSize = dirBytes.length / minBlock8Num;
	  if( (blockSize > 0)
	      && ((blockSize * minBlock8Num) == dirBytes.length) )
	  {
	    block8Sizes.add( blockSize );
	  }
	}
      }
      if( (minBlock16Num > 0) && (block16Sizes.size() != 1) ) {
	int blockSize = dirBytes.length / minBlock16Num;
	if( (blockSize > 0)
	    && ((blockSize * minBlock16Num) == dirBytes.length) )
	{
	  block16Sizes.add( blockSize );
	}
      }

      // Ergebnis ermitteln
      int     block8Size        = 0;
      int     block16Size       = 0;
      boolean block8SizeUnique  = false;
      boolean block16SizeUnique = false;
      if( block8Sizes != null ) {
	if( block8Sizes.isEmpty() ) {
	  if( minBlock8Size > 0 ) {
	    block8Size       = minBlock8Size;
	    block8SizeUnique = true;
	  }
	} else {
	  block8Size       = block8Sizes.last().intValue();
	  block8SizeUnique = (block8Sizes.size() == 1);
	  if( minBlock8Size > block8Size ) {
	    block8Size       = minBlock8Size;
	    block8SizeUnique = false;
	  }
	}
      }
      if( block16Sizes.isEmpty() ) {
	if( minBlock16Size > 0 ) {
	  block16Size       = minBlock16Size;
	  block16SizeUnique = true;
	}
      } else {
	block16SizeUnique = (block16Sizes.size() == 1);
	block16Size       = block16Sizes.last().intValue();
	if( minBlock16Size > block16Size ) {
	  block16Size       = minBlock16Size;
	  block16SizeUnique = false;
	}
      }
      if( block8Size > 0 ) {
	blockNumSizeOut.set( 8 );
	blockSizeOut.set( block8Size );
	blockSizeUniqueOut.set( block8SizeUnique );
	rv = true;
      } else if( block16Size > 0 ) {
	blockNumSizeOut.set( 16 );
	blockSizeOut.set( block16Size );
	blockSizeUniqueOut.set( block16SizeUnique );
	rv = true;
      }
    }
    return rv;
  }


	/* --- private Methoden --- */

  private static int getBlockSize( int entrySize, int nBlocks )
  {
    int blockSize = 0;
    if( (entrySize > 0) && (nBlocks > 0) ) {
      blockSize = 0x80;
      while( (blockSize * nBlocks) < entrySize ) {
	blockSize <<= 1;
      }
    }
    return blockSize;
  }


  private static long getFileSize(
				byte[] dirBytes,
				int    entryPos )
  {
    long fileSize = 0;
    if( (entryPos + 31) < dirBytes.length ) {
      int extentNum = getExtentNumByEntryPos( dirBytes, entryPos );
      int nRecords  = (int) dirBytes[ entryPos + 15 ] & 0xFF;
      fileSize      = (extentNum * 16L * 1024L) + (nRecords * 0x80);
      while( nRecords == 0x80 ) {
	int nextPos = findNextExtentPos( dirBytes, entryPos );
	if( nextPos <= entryPos ) {
	  break;
	}
	entryPos  = nextPos;
	extentNum = getExtentNumByEntryPos( dirBytes, entryPos );
	nRecords  = (int) dirBytes[ entryPos + 15 ] & 0xFF;
	fileSize  = (extentNum * 16L * 1024L) + (nRecords * 0x80);
      }
    }
    return fileSize;
  }


  private static String trimRight( char[] buf, int begPos, int endPos )
  {
    while( endPos > begPos ) {
      if( buf[ endPos - 1 ] != '\u0020' ) {
	break;
      }
      --endPos;
    }
    return String.valueOf( buf, begPos, endPos - begPos);
  }


	/* --- Konstruktor --- */

  private CPMDirUtil()
  {
    // nicht instanziierbar
  }
}
