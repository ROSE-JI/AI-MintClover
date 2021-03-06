/**
 * Copyright (c) 2012-2016 Robert Maupin (Chase)
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 *    1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 *
 *    2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 *
 *    3. This notice may not be removed or altered from any source
 *    distribution.
 */
package cs.util;

import java.awt.Graphics2D;
import java.awt.geom.Arc2D;

import robocode.util.Utils;


/**
 * A class that encapsulates the common functions of a Wave.
 *
 * @author Robert Maupin (Chase)
 *
 */
@SuppressWarnings("serial")
public class Wave extends Vector {
	public long fireTime;
	public double power;
	public double speed;
	public double directAngle;
	public double escapeAngle;
	public NumberRange factorRange = new NumberRange(Byte.MAX_VALUE, Byte.MIN_VALUE);
	protected boolean intersected = false;
	protected boolean completed = false;

	private NumberRange storedFactorRange = new NumberRange();
	private boolean storedIntersected = false;
	private boolean storedCompleted = false;

	public void draw(Graphics2D g, long time) {
		double radius = getRadius(time);
		double escape = Math.abs(escapeAngle);

		g.draw(new Arc2D.Double(x - radius, y - radius, radius * 2, radius * 2, Math.toDegrees(directAngle - escape) - 90, Math
				.toDegrees(escape * 2), Arc2D.OPEN));
	}

	/**
	 * Updates the minimum and maximum intersection factors for a given set of
	 * intersection points.
	 *
	 * @param points
	 *            a set of intersection points in a concatenated array.
	 *            {x1,y1,x2,y2,...}
	 */
	private void expandMinMaxFactors(final double[] points) {
		for(int i = 0; i < points.length; i += 2) {
			final double angle = angleTo(points[i], points[i + 1]);
			final double factor = Utils.normalRelativeAngle(angle - directAngle) / escapeAngle;
			factorRange.expand(factor);
		}
	}

	/**
	 * Get the estimated number of turns until the wave reaches the target given
	 * the current time.
	 * 
	 * @param target
	 *            The target
	 * @param time
	 *            The current time
	 * @return The estimated number of turns until the wave reaches the target.
	 */
	public double getETA(Vector target, long time) {
		final double halfBotWidth = 18 + Math.sin(angleTo(target)) * 7.4558441;
		double distance = distance(target) - getRadius(time) - halfBotWidth;
		return (distance / speed);
	}

	/**
	 * Determines the radius this wave would have given a certain time.
	 *
	 * @param time
	 *            the time to test
	 * @return the radius the wave would have at the given time
	 */
	public double getRadius(final long time) {
		return speed * (time - fireTime);
	}

	/**
	 * Determines if this wave has finished passing the enemy and can now be
	 * processed.
	 *
	 * @return true if it is complete, false otherwise
	 */
	public boolean isCompleted() {
		return completed;
	}

	/**
	 * Determines if this wave has started intersecting the enemy.
	 *
	 * @return true if intersecting, false otherwise.
	 */
	public boolean isIntersected() {
		return intersected;
	}
	
	/*
	 * These state methods are used to store the old information about the wave
	 * while we use the wave to calculate the risk of moving in one or the other
	 * direction, this allows us to avoid using a new class and saves time and
	 * memory.
	 */

	/** Resets the wave to it's initial value. */
	public void resetState() {
		factorRange.set(Byte.MAX_VALUE, Byte.MIN_VALUE);
		intersected = false;
		completed = false;
	}

	/** Restores the previously backed up state. */
	public void restoreState() {
		factorRange.set(storedFactorRange);
		intersected = storedIntersected;
		completed = storedCompleted;
	}

	/** Backs up the current state. */
	public void storeState() {
		storedFactorRange.set(factorRange);
		storedIntersected = intersected;
		storedCompleted = completed;
	}

	/**
	 * Updates this wave. Given the time and the targets position at the given
	 * time. The updates should be given in correct order as the intersection
	 * and completed states are also updated in this method.
	 *
	 * @param time
	 *            The time the target was at the given position.
	 * @param target
	 *            The position the target was at, at the given time.
	 */
	public void update(final long time, final Vector target) {
		boolean intersects = false;
		final double radius = getRadius(time);
		double[] pnts = Tools.intersectRectCircle(target.x - 18, target.y - 18, 36, 36, x, y, radius);
		if(pnts.length != 0) {
			expandMinMaxFactors(pnts);
			intersects = intersected = true;
		}
		final double radius2 = radius + speed;
		pnts = Tools.intersectRectCircle(target.x - 18, target.y - 18, 36, 36, x, y, radius2);
		if(pnts.length != 0) {
			expandMinMaxFactors(pnts);
			intersects = intersected = true;
		}
		// corners
		for(final double[] pnt : new double[][] {
			{ target.x - 18, target.y - 18 },
			{ target.x + 18, target.y - 18 },
			{ target.x - 18, target.y + 18 },
			{ target.x + 18, target.y + 18 } }) {
			
			final double dist = distanceSq(pnt[0], pnt[1]);
			if(dist < radius2 * radius2 && dist > radius * radius) {
				expandMinMaxFactors(pnt);
			}
		}
		if(!intersects && intersected) {
			completed = true;
		}
	}

}
