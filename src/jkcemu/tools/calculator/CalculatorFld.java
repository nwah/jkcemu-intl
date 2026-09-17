/*
 * (c) 2022-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Rechner-Komponente
 */

package jkcemu.tools.calculator;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.lang.LangUtil;


public class CalculatorFld extends JPanel implements
						ActionListener,
						DocumentListener
{
  private static final String DEFAULT_TEXT = "calculator.text.please_enter_character";

  private CalculatorFrm calculatorFrm;
  private boolean       notified;
  private ExprParser    parser;
  private Document      docInput;
  private JTextField    fldInput;
  private JEditorPane   fldOutput;


  public CalculatorFld( CalculatorFrm calculatorFrm )
  {
    this.calculatorFrm = calculatorFrm;
    this.parser        = new ExprParser();
    this.notified      = false;


    setLayout( new GridBagLayout() );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					1, 1,
					1.0, 0.0,
					GridBagConstraints.CENTER,
					GridBagConstraints.HORIZONTAL,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );


    // Bereich Eingabe
    JPanel panelInput = GUIFactory.createPanel( new GridBagLayout() );
    add( panelInput, gbc );

    panelInput.setBorder( GUIFactory.createTitledBorder(
		LangUtil.getText( "calculator.section.input" ) ) );

    GridBagConstraints gbcInput = new GridBagConstraints(
					0, 0,
					1, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );

    panelInput.add( GUIFactory.createLabel( LangUtil.getText(
			"calculator.label.expression" ) ), gbcInput );

    this.fldInput      = GUIFactory.createTextField();
    this.docInput      = this.fldInput.getDocument();
    gbcInput.fill      = GridBagConstraints.HORIZONTAL;
    gbcInput.weightx   = 1.0;
    gbcInput.gridwidth = GridBagConstraints.REMAINDER;
    gbcInput.gridx++;
    panelInput.add( this.fldInput, gbcInput );

    gbcInput.anchor        = GridBagConstraints.EAST;
    gbcInput.fill          = GridBagConstraints.NONE;
    gbcInput.insets.bottom = 0;
    gbcInput.weightx       = 0.0;
    gbcInput.gridwidth     = 1;
    gbcInput.gridy++;
    panelInput.add( GUIFactory.createLabel( LangUtil.getText(
			"calculator.label.binary_number" ) ), gbcInput );

    gbcInput.anchor = GridBagConstraints.WEST;
    gbcInput.gridx++;
    panelInput.add( GUIFactory.createLabel( "$..." ), gbcInput );

    gbcInput.anchor     = GridBagConstraints.EAST;
    gbcInput.insets.top = 0;
    gbcInput.gridx      = 1;
    gbcInput.gridy++;
    panelInput.add( GUIFactory.createLabel( LangUtil.getText(
			"calculator.label.octal_number" ) ), gbcInput );

    gbcInput.anchor = GridBagConstraints.WEST;
    gbcInput.gridx++;
    panelInput.add( GUIFactory.createLabel(
		LangUtil.getText( "calculator.label.q" ) ), gbcInput );

    gbcInput.anchor        = GridBagConstraints.EAST;
    gbcInput.insets.bottom = 5;
    gbcInput.gridx         = 1;
    gbcInput.gridy++;
    panelInput.add( GUIFactory.createLabel(
		LangUtil.getText(
			"calculator.label.hexadecimal_number" ) ), gbcInput );

    gbcInput.anchor = GridBagConstraints.WEST;
    gbcInput.gridx++;
    panelInput.add( GUIFactory.createLabel( LangUtil.getText(
			"calculator.label.0x_h" ) ), gbcInput );


    // Bereich Ausgabe
    JPanel panelOutput = GUIFactory.createPanel( new BorderLayout() );
    gbc.fill    = GridBagConstraints.BOTH;
    gbc.weighty = 1.0;
    gbc.gridy++;
    add( panelOutput, gbc );

    panelOutput.setBorder( GUIFactory.createTitledBorder(
		LangUtil.getText( "calculator.section.output" ) ) );

    this.fldOutput = GUIFactory.createEditorPane();
    this.fldOutput.setContentType( "text/html" );
    EmuUtil.setText( this.fldOutput, LangUtil.getText( DEFAULT_TEXT ) );
    this.fldOutput.setBorder( BorderFactory.createLoweredBevelBorder() );
    this.fldOutput.setEditable( false );
    panelOutput.add(
		GUIFactory.createScrollPane( this.fldOutput ),
		BorderLayout.CENTER );
  }


  public void clear()
  {
    this.fldInput.setText( "" );
    EmuUtil.setText( this.fldOutput, LangUtil.getText( DEFAULT_TEXT ) );
  }


  public void requestFocusToInput()
  {
    this.fldInput.requestFocus();
  }


  public void setPreferredOutputSize( Dimension size )
  {
    this.fldOutput.setPreferredSize( size );
  }


	/* --- ActionListener --- */

  @Override
  public void actionPerformed( ActionEvent e )
  {
    if( e.getSource() == this.fldInput )
      updOutput();
  }


	/* --- DocumentListener --- */

  @Override
  public void changedUpdate( DocumentEvent e )
  {
    docChanged( e );
  }


  @Override
  public void insertUpdate( DocumentEvent e )
  {
    docChanged( e );
  }


  @Override
  public void removeUpdate( DocumentEvent e )
  {
    docChanged( e );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void addNotify()
  {
    super.addNotify();
    if( !this.notified ) {
      this.notified = true;
      this.fldInput.addActionListener( this );
      this.fldInput.addFocusListener( this.calculatorFrm );
      if( this.docInput != null ) {
	this.docInput.addDocumentListener( this );
      }
      this.fldOutput.addFocusListener( this.calculatorFrm );
    }
  }


  @Override
  public void removeNotify()
  {
    if( this.notified ) {
      this.notified = false;
      this.fldInput.removeActionListener( this );
      this.fldInput.removeFocusListener( this.calculatorFrm );
      if( this.docInput != null ) {
	this.docInput.removeDocumentListener( this );
      }
      this.fldOutput.removeFocusListener( this.calculatorFrm );
    }
    super.removeNotify();
  }


	/* --- private Methoden --- */

  private void docChanged( DocumentEvent e )
  {
    if( (this.docInput != null) && (e.getDocument() == this.docInput) )
      updOutput();
  }


  private void updOutput()
  {
    String result = LangUtil.getText( DEFAULT_TEXT );
    String text   = this.fldInput.getText();
    if( text != null ) {
      int len = text.length();
      if( len > 0 ) {
	boolean       status = false;
	StringBuilder buf    = new StringBuilder( 256 );
	try {
	  if( len == 1 ) {
	    char ch = text.charAt( 0 );
	    if( Character.isDefined( ch ) ) {
	      appendResultRow(
			buf,
			LangUtil.getText(
				"calculator.text.unicode_character" ),
			(int) ch );
	    }
	  }
	  appendResultRow(
			buf,
			LangUtil.getText(
				"calculator.text.result_expression" ),
			parser.parseExpr( text ) );
	  appendResultEnd( buf );
	}
	catch( ParseException ex ) {
	  if( buf.length() > 0 ) {
	    appendResultEnd( buf );
	  } else {
	    int pos = ex.getErrorOffset();
	    if( pos > len ) {
	      pos = len;
	    }
	    if( pos < 0 ) {
	      pos = 0;
	    }
	    buf.append( "<html>\n<p>" );
	    EmuUtil.appendHTML( buf, ex.getMessage() );
	    buf.append( "</p>\n<p>" );
	    EmuUtil.appendHTML( buf, text.substring( 0, pos ) );
	    buf.append( " <b>?</b> " );
	    EmuUtil.appendHTML( buf, text.substring( pos ) );
	    buf.append( "</p>\n</html>\n" );
	  }
	}
	result = buf.toString();
      }
    }
    EmuUtil.setText( this.fldOutput, result );
  }


  private void appendResultRow(
			StringBuilder buf,
			String        title,
			Number        value )
  {
    if( value != null ) {
      boolean isLong = false;
      long    lValue = 0;
      if( value instanceof BigDecimal ) {
	if( ((BigDecimal) value).scale() <= 0 ) {
	  try {
	    lValue = ((BigDecimal) value).longValueExact();
	    isLong = true;
	  }
	  catch( ArithmeticException ex ) {}
	}
      }
      if( value instanceof BigInteger ) {
	if( ((BigInteger) value).bitLength() < 64 ) {
	  lValue = value.longValue();
	  isLong = true;
	}
      }
      else if( (value instanceof Integer) || (value instanceof Long) ) {
	lValue = value.longValue();
	isLong = true;
      }
      if( buf.length() < 1 ) {
	buf.append( "<html>\n"
		+ "<table border=1>\n"
		+ "<tr><th nowrap></th><th nowrap>Hex</th>"
		+ "<th nowrap>Dezimal</th><th nowrap>Oktal</th>"
		+ "<th nowrap>Bin&auml;r</th>"
		+ "<th nowrap>Unicode-Zeichen</th></tr>\n" );
      }
      buf.append( "<tr><td nowrap>" );
      EmuUtil.appendHTML( buf, title );
      buf.append( ":</td><td>" );
      if( isLong ) {
	EmuUtil.appendHTML( buf, Long.toHexString( lValue ).toUpperCase() );
      }
      buf.append( "</td><td nowrap>" );
      String decText = null;
      if( value instanceof BigDecimal ) {
	if( Math.abs( ((BigDecimal) value).scale() ) < 10 ) {
	  decText = ((BigDecimal) value).toPlainString();
	}
      }
      if( decText == null ) {
	decText = value.toString();
      }
      EmuUtil.appendHTML( buf, decText );
      buf.append( "</td><td nowrap>" );
      if( isLong ) {
	EmuUtil.appendHTML( buf, Long.toOctalString( lValue ) );
      }
      buf.append( "</td><td nowrap>" );
      if( isLong ) {
	EmuUtil.appendHTML( buf, Long.toBinaryString( lValue ) );
      }
      buf.append( "</td><td nowrap>" );
      if( isLong ) {
	if( (lValue >= 0) && (lValue < Integer.MAX_VALUE) ) {
	  if( (lValue > '\u0020') && (lValue <= '\u007E') ) {
	    EmuUtil.appendHTML( buf, Character.toString( (char) lValue ) );
	  } else if( Character.isDefined( (int) lValue ) ) {
	    buf.append( String.format( "&#%d;", lValue ) );
	  }
	}
      }
      buf.append( "</td></tr>\n" );
    }
  }


  private void appendResultEnd( StringBuilder buf )
  {
    buf.append( "</table>\n</html>\n" );
  }
}
