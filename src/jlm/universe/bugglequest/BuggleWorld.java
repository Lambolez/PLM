package jlm.universe.bugglequest;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import jlm.core.model.Game;
import jlm.core.model.ProgrammingLanguage;
import jlm.core.utils.ColorMapper;
import jlm.core.utils.FileUtils;
import jlm.core.utils.InvalidColorNameException;
import jlm.universe.BrokenWorldFileException;
import jlm.universe.Direction;
import jlm.universe.Entity;
import jlm.universe.EntityControlPanel;
import jlm.universe.GridWorld;
import jlm.universe.GridWorldCell;
import jlm.universe.World;
import jlm.universe.bugglequest.exception.AlreadyHaveBaggleException;
import jlm.universe.bugglequest.ui.BuggleButtonPanel;
import jlm.universe.bugglequest.ui.BuggleWorldView;


public class BuggleWorld extends GridWorld {

	public BuggleWorld(String name, int x, int y) {
		super(name,x,y);
	}
	@Override
	protected GridWorldCell newCell(int x, int y) {
		return new BuggleWorldCell(this, x, y);
	}
	/** 
	 * Create a new world being almost a copy of the first one. Beware, all the buggles of the copy are changed to BuggleRaw. 
	 * @param world2
	 */
	public BuggleWorld(BuggleWorld world2) {
		super(world2);
	}

	/**
	 * Reset the content of a world to be the same than the one passed as argument
	 * does not affect the name of the initial world.
	 */
	@Override
	public void reset(World iw) {
		BuggleWorld initialWorld = (BuggleWorld)iw;
		for (int i = 0; i < sizeX; i++)
			for (int j = 0; j < sizeY; j++) {
				BuggleWorldCell c = (BuggleWorldCell) initialWorld.getCell(i, j);
				cells[i][j] = new BuggleWorldCell(c, this);
			}
		easter=false;

		super.reset(initialWorld);
	}	
	@Override
	public void setWidth(int w) {
		super.setWidth(w);
		if (selectedCell != null && selectedCell.getX()>=w)
			selectedCell = null;
	}
	@Override
	public void setHeight(int h) {
		super.setHeight(h);
		if (selectedCell != null && selectedCell.getY()>=h)
			selectedCell = null;
	}

	@Override
	public BuggleWorldView getView() {
		return new BuggleWorldView(this);
	}
	@Override
	public EntityControlPanel getEntityControlPanel() {
		return new BuggleButtonPanel();
	}

	public boolean easter = false;
	/* IO related */
	private String strip(String s) {
		return s.replaceAll(";.*", "");
	}

	@Override
	public void readFromFile(String path) throws IOException, BrokenWorldFileException {
		BufferedReader reader = FileUtils.newFileReader(path, "map", false);
		
		/* Get the world name from the first line */
		String line = reader.readLine();
		if (line == null)
			throw new BrokenWorldFileException(Game.i18n.tr(
					"{0}.map: this file does not seem to be a serialized BuggleWorld (the file is empty!)",path));
		
		Pattern p = Pattern.compile("^BuggleWorld: ");
		Matcher m = p.matcher(line);
		if (!m.find())
			throw new RuntimeException(Game.i18n.tr(
					"{0}.map: This file does not seem to be a serialized BuggleWorld (malformated first line: {1})", path, line));
		setName(m.replaceAll(""));
		
		/* Get the dimension from the second line that is eg "Size: 20x20" */
		line = reader.readLine();
		if (line == null)
			throw new RuntimeException(Game.i18n.tr("" +
					"{0}.map: End of file reached before world size specification",path));
		p = Pattern.compile("^Size: (\\d+)x(\\d+)$");
		m = p.matcher(line);
		if (!m.find()) 
			throw new RuntimeException(Game.i18n.tr("{0}.map:1: Expected 'Size: NNxMM' but got '{0}'", line));
		int width = Integer.parseInt(m.group(1)); 
		int height = Integer.parseInt(m.group(2));

		create(width, height);
		while (!entities.isEmpty())
			entities.remove(0);
		
		line = reader.readLine();
		
		Pattern bugglePattern = Pattern.compile("^Buggle\\((\\d+),(\\d+)\\): (\\w+),(\\w+),(\\w+),(.+)$"); // direction, color, brush, name
		Matcher buggleMatcher = bugglePattern.matcher(line);
		String cellFmt = "^Cell\\((\\d+),(\\d+)\\): ([^,]+?),(\\w+),(\\w+),(\\w+),(.*)$";
		Pattern cellPattern = Pattern.compile(cellFmt);
		Matcher cellMatcher = cellPattern.matcher(line);

		if (cellMatcher.matches() || buggleMatcher.matches()) {
			/* This world is using the new syntax */
			
			do {
				cellMatcher = cellPattern.matcher(line);
				buggleMatcher = bugglePattern.matcher(line);

				if (buggleMatcher.matches()) { 
					int x=Integer.parseInt( buggleMatcher.group(1) );
					int y=Integer.parseInt( buggleMatcher.group(2) );
					
					if (x<0 || x > width || y<0 || y>height)
						throw new BrokenWorldFileException(i18n.tr(
								"Cannot put a buggle on coordinate {0},{1}: that's out of the world",x,y));
					
					String dirName = buggleMatcher.group(3);
					Direction direction;
					if (dirName.equalsIgnoreCase("north"))
						direction = Direction.NORTH;
					else if (dirName.equalsIgnoreCase("south"))
						direction = Direction.SOUTH;
					else if (dirName.equalsIgnoreCase("east"))
						direction = Direction.EAST;
					else if (dirName.equalsIgnoreCase("west"))
						direction = Direction.WEST;
					else 
						throw new BrokenWorldFileException(i18n.tr(
								"Invalid buggle's direction: {0}", buggleMatcher.group(3)));
					
					Color color;
					try {
						color = ColorMapper.name2color( buggleMatcher.group(4));
					} catch (InvalidColorNameException e) {
						throw new BrokenWorldFileException(i18n.tr(
								"Invalid buggle's color name: {0}", buggleMatcher.group(4)));
					}
					Color brushColor;
					try {
						brushColor = ColorMapper.name2color( buggleMatcher.group(5));
					} catch (InvalidColorNameException e) {
						throw new BrokenWorldFileException(i18n.tr(
								"Invalid buggle's color name: {0}", buggleMatcher.group(5)));
					}
					String name = buggleMatcher.group(6);

					new Buggle(this, name, x, y, direction, color, brushColor);
				} else if (cellMatcher.matches()) {
					/* Get the info */
					int x=Integer.parseInt( cellMatcher.group(1) );
					int y=Integer.parseInt( cellMatcher.group(2) );
					
					if (x<0 || x > width || y<0 || y>height)
						throw new BrokenWorldFileException(i18n.tr(
								"Cannot define a cell on coordinate {0},{1}: that's out of the world",x,y));

					
					String colorName = cellMatcher.group(3);
					Color color;
					String baggleFlag = cellMatcher.group(4);
					String topWallFlag = cellMatcher.group(5);
					String leftWallFlag = cellMatcher.group(6);
					String content = cellMatcher.group(7);
					
					try {
						color = ColorMapper.name2color(colorName);
					} catch (InvalidColorNameException e) {
						throw new BrokenWorldFileException(i18n.tr("Invalid color name: {0}",colorName));
					}
					
					/* Make sure that this info makes sense */
					if (!baggleFlag.equalsIgnoreCase("baggle") && !baggleFlag.equalsIgnoreCase("nobaggle"))
						throw new BrokenWorldFileException(i18n.tr(
								"Expecting 'baggle' or 'nobaggle' but got {0} instead",baggleFlag));
					
					if (!topWallFlag.equalsIgnoreCase("topwall") && !topWallFlag.equalsIgnoreCase("notopwall"))
						throw new BrokenWorldFileException(i18n.tr(
								"Expecting 'topwall' or 'notopwall' but got {0} instead",topWallFlag));
					
					if (!leftWallFlag.equalsIgnoreCase("leftwall") && !leftWallFlag.equalsIgnoreCase("noleftwall"))
						throw new BrokenWorldFileException(i18n.tr(
								"Expecting 'leftwall' or 'noleftwall' but got {0} instead",leftWallFlag));
					
					/* Use the info */
					BuggleWorldCell cell = new BuggleWorldCell(this, x, y);

					if (baggleFlag.equalsIgnoreCase("baggle"))
						try {
							cell.setBaggle(new Baggle(cell));
						} catch (AlreadyHaveBaggleException e) {
							throw new BrokenWorldFileException(i18n.tr(
									"The cell {0},{1} seem to be defined more than once. At least, there is two baggles here, which is not allowed.",x,y));
						}

					if (topWallFlag.equalsIgnoreCase("topwall"))
						cell.putTopWall();
					if (leftWallFlag.equalsIgnoreCase("leftwall"))
						cell.putLeftWall();		

					cell.setColor(color);
					
					if (content.length()>0)
						cell.setContent(content);

					setCell(cell, x, y);
				} else {
					throw new BrokenWorldFileException(i18n.tr(
							"Parse error. I was expecting a cell or a buggle description but got: {0}",line));					
				}
				
				line = reader.readLine();
			} while (line != null);
		} else {
			System.out.println("Warning, the world "+path+" uses the old syntax\n"+line); /* FIXME Kill this branch after the transition */
			
			/* read each cell, one after the other */
			for (int x = 0; x < getWidth(); x++) {
				for (int y = 0; y < getHeight(); y++) {
					BuggleWorldCell cell = new BuggleWorldCell(this, x, y);
					if (line == null) 
						throw new IOException("File ending before the map was read completely");

					line = strip(line); // strip '; comment'



					int index1 = line.indexOf("),");
					int index2 = line.indexOf(',', index1+2);
					int index3 = line.indexOf(',', index2+1);
					int index4 = line.length()-2;

					boolean baggleFlag = Boolean.parseBoolean(line.substring(index1+2, index2));
					boolean topWallFlag = Boolean.parseBoolean(line.substring(index2+1, index3));
					boolean leftWallFlag = Boolean.parseBoolean(line.substring(index3+1, index4));
					if (baggleFlag)
						try {
							cell.setBaggle(new Baggle(cell));
						} catch (AlreadyHaveBaggleException e) {
							e.printStackTrace();
						}

					if (topWallFlag)
						cell.putTopWall();
					if (leftWallFlag)
						cell.putLeftWall();		

					/* parse color */
					String s =line.substring(1, index1+1); 
					index1 = s.indexOf(",");
					index2 = s.indexOf(',', index1+1);
					index3 = s.length()-1;

					int r = Integer.parseInt(s.substring(1, index1));
					int g = Integer.parseInt(s.substring(index1+1, index2));
					int b = Integer.parseInt(s.substring(index2+1, index3));

					cell.setColor(new Color(r,g,b));

					setCell(cell, x, y);
					line = reader.readLine();
				}
			}
		}
	}

	@Override
	public void writeToFile(BufferedWriter writer) throws IOException {

		writer.write("BuggleWorld: "+getName() + "\n");
		writer.write("Size: "+getWidth() + "x"+ getHeight() + "\n");

		for (Entity e : getEntities()) {
			AbstractBuggle b = (AbstractBuggle) e;
			writer.write("Buggle("+b.getX()+","+b.getY()+"): ");
			
			if (b.getDirection().equals(Direction.NORTH)) 
				writer.write("north,");
			if (b.getDirection().equals(Direction.SOUTH)) 
				writer.write("south,");
			if (b.getDirection().equals(Direction.EAST)) 
				writer.write("east,");
			if (b.getDirection().equals(Direction.WEST)) 
				writer.write("west,");
			
			writer.write(ColorMapper.color2name(b.getColor())+",");
			writer.write(ColorMapper.color2name(b.getBrushColor())+",");
			writer.write(b.getName());
			writer.write("\n");
		}
			
		
		for (int x = 0; x < getWidth(); x++) {
			for (int y = 0; y < getHeight(); y++) {
				BuggleWorldCell cell = (BuggleWorldCell) getCell(x, y);

				if ((!cell.getColor().equals(Color.white)) || cell.hasBaggle() || 
						cell.hasLeftWall() || cell.hasTopWall() || cell.hasContent()
						) {
					
					writer.write("Cell("+x+","+y+"): ");
					writer.write(ColorMapper.color2name(cell.getColor()));
					
					if (cell.hasBaggle()) 
						writer.write("baggle,");
					else 
						writer.write("nobaggle,");
					
					if (cell.hasTopWall()) 
						writer.write("topwall,");
					else 
						writer.write("notopwall,");

					if (cell.hasLeftWall()) 
						writer.write("leftwall,");
					else 
						writer.write("noleftwall,");
					
					if (cell.hasContent())
						writer.write(cell.getContent());
					writer.write("\n");
				}
			}
		}
	}

	@Override
	public String toString() {
		return super.toString(); 
	}
	@Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		//result = PRIME * result + ((entities == null) ? 0 : entities.hashCode());
		result = PRIME * result + sizeX;
		result = PRIME * result + sizeY;
		result = PRIME * result + Arrays.hashCode(cells);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final BuggleWorld other = (BuggleWorld) obj;
		if (sizeX != other.sizeX)
			return false;
		if (sizeY != other.sizeY)
			return false;
		for (int x=0; x<getWidth(); x++) 
			for (int y=0; y<getHeight(); y++) 
				if (!getCell(x, y).equals(other.getCell(x, y)))
					return false;

		return super.equals(obj);
	}

	/* Cell selection is particularly important to world edition */
	BuggleWorldCell selectedCell=null;
	public BuggleWorldCell getSelectedCell() {
		return selectedCell;
	}
	public void setSelectedCell(int x, int y) {
		selectedCell = getCell(x,y);
	}
	public void unselectCell() {
		selectedCell = null;
	}
	
	/* adapters to the cells */
	public BuggleWorldCell getCell(int x, int y) {
		return (BuggleWorldCell) super.getCell(x, y);
	}
	public void setColor(int x, int y, Color c) {
		getCell(x, y).setColor(c);
	}
	public void addContent(int x, int y, String string) {
		getCell(x, y).addContent(string);
	}

	public void putTopWall(int x, int y) {
		getCell(x, y).putTopWall();		
	}

	public void putLeftWall(int x, int y) {
		getCell(x, y).putLeftWall();		
	}
	public void newBaggle(int x, int y) throws AlreadyHaveBaggleException {
		getCell(x, y).newBaggle();		
	}
	@Override
	public void setupBindings(ProgrammingLanguage lang,ScriptEngine engine) throws ScriptException {
		if (lang.equals(Game.PYTHON)) {
			engine.put("Direction", Direction.class);
			engine.put("Color", Color.class);
			engine.eval(
				"def forward(steps=1):\n"+
				"	entity.forward(steps)\n"+
				"def backward(steps=1):\n"+
				"	entity.backward(steps)\n"+
				"def turnLeft():\n"+
				"	entity.turnLeft()\n"+
				"def turnBack():\n"+
				"	entity.turnBack()\n"+
				"def turnRight():\n"+
				"	entity.turnRight()\n"+
				"\n"+
				"def getWorldHeight():\n"+
				"	return entity.getWorldHeight()\n"+
				"def getWorldWidth():\n"+
				"	return entity.getWorldWidth()\n"+
				"def getX():\n"+
				"	return entity.getX()\n"+
				"def getY():\n"+
				"	return entity.getY()\n"+
				"def setX(x):\n"+
				"	entity.setX(x)\n"+
				"def setY(y):\n"+
				"	entity.setY(y)\n"+
				"def setPos(x,y):\n"+
				"	entity.setPos(x,y)\n"+
				"def brushDown():\n"+
				"   entity.brushDown()\n"+
				"def brushUp():\n"+
				"   entity.brushUp()\n" +
				"def isFacingWall():" +
				"	return entity.isFacingWall()\n"+
				"def getGroundColor():\n"+
				"   return entity.getGroundColor()\n"+
				
				"def errorMsg(str):\n"+
				"  entity.seenError(str)\n"+
				
				"def isOverBaggle():\n"+
				"	return entity.isOverBaggle()\n"+
				"def isCarryingBaggle():\n"+
				"	return entity.isCarryingBaggle()\n"+
				"def pickUpBaggle():\n"+
				"	return entity.pickUpBaggle()\n"+
				"def dropBaggle():\n"+
				"	return entity.dropBaggle()\n"+
				
				"def isOverMessage():\n"+
				"	return entity.isOverMessage()\n"+
				"def readMessage():\n"+
				"	return entity.readMessage()\n"+
				"def clearMessage():\n"+
				"   entity.clearMessage()\n"+
				"def writeMessage(msg):\n"+
				"   entity.writeMessage(msg)\n"+
				
				"def getDirection():\n"+
				"   return entity.getDirection()\n"+
				
				"def setBrushColor(c):\n"+
				"    entity.setBrushColor(c)\n"+
				"def getBrushColor():\n"+
				"    return entity.getBrushColor()\n"
						);		
		} else {
			throw new RuntimeException("No binding of BuggleWorld for "+lang);
		}
	}
	@Override
	public String diffTo(World world) {
		BuggleWorld other = (BuggleWorld) world;
		StringBuffer sb = new StringBuffer();
		for (int x=0; x<getWidth(); x++) 
			for (int y=0; y<getHeight(); y++) 
				if (!getCell(x, y).equals(other.getCell(x, y))) 
					sb.append(i18n.tr("  In ({0},{1})",x,y)+  getCell(x, y).diffTo(other.getCell(x, y))+".\n");
		for (int i=0; i<entities.size(); i++)  
			if (! entities.get(i).equals(other.entities.get(i))) 
				sb.append(i18n.tr("  Something is wrong about buggle \"{0}\":\n",entities.get(i).getName())+
						((AbstractBuggle) entities.get(i)).diffTo((AbstractBuggle) other.entities.get(i)));
		return sb.toString();
	}

}
