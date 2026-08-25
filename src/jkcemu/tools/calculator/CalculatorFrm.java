/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Rechner
 */

package jkcemu.tools.calculator;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.EventObject;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import jkcemu.Main;
import jkcemu.base.BaseFrm;
import jkcemu.base.EmuUtil;
import jkcemu.base.HelpFrm;
import jkcemu.base.GUIFactory;
import jkcemu.base.TabTitleFld;
import jkcemu.text.TextUtil;
import jkcemu.lang.LangUtil;


public class CalculatorFrm extends BaseFrm implements
						FocusListener,
						MenuListener
{
  public static final String TITLE = Main.APPNAME + " Rechner";

  private static final String HELP_PAGE = "/help/tools/calculator.htm";


  private static CalculatorFrm instance = null;

  private Clipboard      clipboard;
  private CalculatorFld  firstCalcFld;
  private int            nextCalcNum;
  private JTabbedPane    tabbedPane;
  private JTextComponent focusedTextFld;
  private JMenu          mnuEdit;
  private JMenuItem      mnuNewTab;
  private JMenuItem      mnuCloseTab;
  private JMenuItem      mnuClose;
  private JMenuItem      mnuCut;
  private JMenuItem      mnuCopy;
  private JMenuItem      mnuPaste;
  private JMenuItem      mnuSelectAll;
  private JMenuItem      mnuHelpContent;


  public static CalculatorFrm open()
  {
    if( instance == null ) {
      instance = new CalculatorFrm();
    }
    EmuUtil.showFrame( instance );
    return instance;
  }


	/* --- FocusListener --- */

  @Override
  public void focusGained( FocusEvent e )
  {
    JTextComponent fld = null;
    Component      c   = e.getComponent();
    if( c != null ) {
      if( c instanceof JTextComponent ) {
	fld = (JTextComponent) c;
      }
    }
    this.focusedTextFld = fld;
  }


  @Override
  public void focusLost( FocusEvent e )
  {
    // leer
  }


	/* --- MenuListener --- */

  @Override
  public void menuCanceled( MenuEvent e )
  {
    // leer
  }


  @Override
  public void menuDeselected( MenuEvent e )
  {
    // leer
  }


  @Override
  public void menuSelected( MenuEvent e )
  {
    updEditMenuItemsEnabled(
			this.focusedTextFld,
			this.mnuCut,
			this.mnuCopy,
			this.mnuPaste,
			this.mnuSelectAll );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( src == this.mnuNewTab ) {
      rv = true;
      doNewTab();
    }
    else if( src == this.mnuCloseTab ) {
      rv = true;
      closeTabAt( this.tabbedPane.getSelectedIndex() );
    }
    else if( src == this.mnuClose ) {
      rv = true;
      doClose();
    }
    else if( src == this.mnuCut ) {
      rv = true;
      if( this.focusedTextFld != null ) {
	this.focusedTextFld.cut();
      }
    }
    else if( src == this.mnuCopy ) {
      rv = true;
      if( this.focusedTextFld != null ) {
	this.focusedTextFld.copy();
      }
    }
    else if( src == this.mnuPaste ) {
      rv = true;
      if( this.focusedTextFld != null ) {
	this.focusedTextFld.paste();
      }
    }
    else if( src == this.mnuSelectAll ) {
      rv = true;
      if( this.focusedTextFld != null ) {
	this.focusedTextFld.selectAll();
      }
    }
    else if( src == this.mnuHelpContent ) {
      rv = true;
      HelpFrm.openPage( HELP_PAGE );
    }
    else if( e instanceof TabTitleFld.TabCloseEvent ) {
      rv = true;
      closeTabAt( ((TabTitleFld.TabCloseEvent) e).getTabIndex() );
    }
    return rv;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = false;
    if( Main.isTopFrm( this ) ) {
      rv = EmuUtil.closeOtherFrames( this );
      if( rv ) {
	rv = super.doClose();
      }
      if( rv ) {
	Main.exitSuccess();
      }
    } else {
      rv = super.doClose();
    }
    if( rv ) {
      // Aufraeumen fuer erneute Oeffnen
      this.nextCalcNum = 1;
      this.tabbedPane.removeAll();
      this.firstCalcFld.clear();
      addCalcTab( this.firstCalcFld );
    }
    return rv;
  }


  @Override
  public void windowOpened( WindowEvent e )
  {
    if( e.getWindow() == this ) {
      if( this.firstCalcFld != null ) {
	this.firstCalcFld.requestFocusToInput();
      }
    }
  }


	/* --- Aktionen im Menu Bearbeiten --- */

  private void doNewTab()
  {
    CalculatorFld calcFld = new CalculatorFld( this );
    addCalcTab( calcFld );
    calcFld.requestFocusToInput();
  }


	/* --- Konstruktor --- */

  private CalculatorFrm()
  {
    this.clipboard      = null;
    this.focusedTextFld = null;
    this.nextCalcNum    = 1;
    setTitle( TITLE );


    // Menu Datei
    JMenu mnuFile = createMenuFile();

    this.mnuNewTab = createMenuItem( "Neues Rechner-Unterfenster");
    this.mnuNewTab.setAccelerator(
		KeyStroke.getKeyStroke(
				KeyEvent.VK_N,
				InputEvent.CTRL_DOWN_MASK ) );
    mnuFile.add( this.mnuNewTab );

    this.mnuCloseTab = createMenuItem( "Unterfenster schlie\u00Dfen");
    this.mnuCloseTab.setAccelerator(
		KeyStroke.getKeyStroke(
				KeyEvent.VK_W,
				InputEvent.CTRL_DOWN_MASK ) );
    mnuFile.add( this.mnuCloseTab );
    mnuFile.addSeparator();

    this.mnuClose = createMenuItemClose();
    mnuFile.add( this.mnuClose );


    // Menu Bearbeiten
    this.mnuEdit = createMenuEdit();

    this.mnuCut = createMenuItemCut( true );
    this.mnuEdit.add( this.mnuCut );

    this.mnuCopy = createMenuItemCopy( true );
    this.mnuEdit.add( this.mnuCopy );

    this.mnuPaste = createMenuItemPaste( true );
    this.mnuEdit.add( this.mnuPaste );
    this.mnuEdit.addSeparator();

    this.mnuSelectAll = createMenuItemSelectAll( true );
    this.mnuEdit.add( this.mnuSelectAll );


    // Menu Hilfe
    JMenu mnuHelp = createMenuHelp();

    this.mnuHelpContent = createMenuItem( "Hilfe zum Rechner..." );
    mnuHelp.add( this.mnuHelpContent );


    // Menuleiste zusammenbauen
    setJMenuBar(
	GUIFactory.createMenuBar( mnuFile, this.mnuEdit, mnuHelp ) );


    // Fensterinhalt
    setLayout( new BorderLayout() );
    this.firstCalcFld = new CalculatorFld( this );
    this.tabbedPane   = GUIFactory.createTabbedPane();
    addCalcTab( this.firstCalcFld );
    add( this.tabbedPane, BorderLayout.CENTER );


    // Zwischenablage
    Toolkit tk = EmuUtil.getToolkit( this );
    if( tk != null ) {
      this.clipboard = tk.getSystemClipboard();
    }


    // Fenstergroesse
    setResizable( true );
    if( !applySettings( Main.getProperties() ) ) {
      firstCalcFld.setPreferredOutputSize( new Dimension( 300, 100 ) );
      pack();
      setScreenCentered();
      firstCalcFld.setPreferredOutputSize( null );
    }


    // Listener
    this.mnuEdit.addMenuListener( this );
  }


	/* --- private Methoden --- */

  private void addCalcTab( CalculatorFld calcFld )
  {
    TabTitleFld.addTabTo(
		this.tabbedPane,
		LangUtil.tr( "Rechner {0}", this.nextCalcNum++ ),
		calcFld,
		this );
    this.tabbedPane.setSelectedComponent( calcFld );
  }


  private void closeTabAt( int idx )
  {
    if( idx >= 0 ) {
      try {
	this.tabbedPane.remove( idx );
	if( this.tabbedPane.getTabCount() == 0 ) {
	  doClose();
	}
      }
      catch( IndexOutOfBoundsException e ) {}
    }
  }


  private void updEditMenuItemsEnabled(
				Component c,
				JMenuItem mnuCut,
				JMenuItem mnuCopy,
				JMenuItem mnuPaste,
				JMenuItem mnuSelectAll )
  {
    boolean stateCut       = false;
    boolean stateCopy      = false;
    boolean statePaste     = false;
    boolean stateSelectAll = false;
    if( c != null ) {
      if( c instanceof JTextComponent ) {
	boolean editable  = ((JTextComponent) c).isEditable();
	stateCopy         = TextUtil.isTextSelected( c );
	stateCut          = stateCopy && editable;
	if( editable && (this.clipboard != null) ) {
	  try {
	    statePaste = this.clipboard.isDataFlavorAvailable(
						DataFlavor.stringFlavor );
	  }
	  catch( IllegalStateException ex ) {}
	}
	Document doc = ((JTextComponent) c).getDocument();
	if( doc != null ) {
	  if( doc.getLength() > 0 ) {
	    stateSelectAll = true;
	  }
	}
      }
    }
    mnuCut.setEnabled( stateCut );
    mnuCopy.setEnabled( stateCopy );
    mnuPaste.setEnabled( statePaste );
    mnuSelectAll.setEnabled( stateSelectAll );
  }
}
