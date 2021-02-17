import java.io.File;

import fr.holo.interpreter.JIPL;

public class Example {
	
	public static void main(String[] args) {
		JIPL.run(new File("example.txt"), JIPL.getGlobalContext());
	}
	
}
