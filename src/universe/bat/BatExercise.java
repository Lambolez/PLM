package universe.bat;

import java.util.List;

import jlm.lesson.ExerciseTemplatingEntity;
import jlm.lesson.Lesson;
import jlm.universe.World;

public abstract class BatExercise extends ExerciseTemplatingEntity {
	public static final boolean INVISIBLE = false;
	public static final boolean VISIBLE = true;
	
	public BatExercise(Lesson lesson) {
		super(lesson);
		entityName = getClass().getCanonicalName()+".Entity";
	}

	protected void setup(World[] ws, String entName) {
		for (World w : ws) {
			BatWorld bw = (BatWorld) w;
			String name=entName+"(";
			for (Object o:w.getParameters()) {
				name+=o.toString()+",";
			}
			name=name.substring(0,name.length()-1);
			name+=")";
			bw.setName(name);
			w.addEntity(new BatEntity());
		}
		super.setup(ws,entName,
				"import universe.bat.BatEntity; "+
		        "import universe.bat.BatWorld; "+
		        "import jlm.universe.World; "+
		        "public class "+entName+" extends BatEntity { ");
	}

	@Override
	public void runDemo(List<Thread> runnerVect){
		/* No demo in bat exercises */
	}
	//@Override
	//public boolean check() {
	//	return false;
	//}
	
}
