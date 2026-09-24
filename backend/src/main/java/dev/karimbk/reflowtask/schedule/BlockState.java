package dev.karimbk.reflowtask.schedule;

/**
 * What became of a block. Only PLANNED blocks are ever moved or rebuilt by a replan; DONE and
 * MISSED blocks are history.
 */
public enum BlockState {

	PLANNED,

	/** The person marked this part done. Its minutes count against the task's estimate. */
	DONE,

	/** Its time passed while unfinished. The work was planned again; the block stays as a record. */
	MISSED

}
