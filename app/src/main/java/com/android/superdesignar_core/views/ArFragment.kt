package com.android.superdesignar_core.views

import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.android.superdesignar_core.R
import com.android.superdesignar_core.databinding.FragmentArBinding
import com.android.superdesignar_core.viewmodel.ViewModel
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.util.*


class ArFragment : Fragment(R.layout.fragment_ar), Scene.OnUpdateListener {
    private lateinit var viewRenderable: ViewRenderable
    private lateinit var distanceInMeters: CardView
    private lateinit var lineBetween: AnchorNode
    private var lengthLabel: AnchorNode? = null
    private var node2Pos: Vector3? = null
    private var node1Pos: Vector3? = null
    private var initialAnchor: AnchorNode? = null
    lateinit var binding: FragmentArBinding
    lateinit var arFragment: ArFragment
    lateinit var arSceneView: ArSceneView
    private var anchorNodeTemp: AnchorNode? = null
    private var pointRender: ModelRenderable? = null
    private var aimRender: ModelRenderable? = null
    private var widthLineRender: ModelRenderable? = null

    private var heightLineRender: ModelRenderable? = null
    private val currentAnchorNode = ArrayList<AnchorNode>()
    private val labelArray: ArrayList<AnchorNode> = ArrayList()
    private val currentAnchor = ArrayList<Anchor?>()
    private var totalLength = 0f
    private var difference: Vector3? = null

    private val placedAnchors = ArrayList<Anchor>()
    private val placedAnchorNodes: ArrayList<AnchorNode> = arrayListOf()
    private val midAnchors: MutableMap<String, Anchor> = mutableMapOf()
    private val midAnchorNodes: MutableMap<String, AnchorNode> = mutableMapOf()

    private val viewModel: ViewModel by activityViewModels()

    private val fromGroundNodes = ArrayList<List<Node>>()
    private var distanceCardViewRenderable: ViewRenderable? = null

    private var cubeRenderable: ModelRenderable? = null


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentArBinding.bind(view)
        arFragment = childFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment

        binding.btnClear.setOnClickListener {
            clearAnchors()
            clearAllAnchors()
        }

        binding.floatingActionButton2.setOnClickListener {
            viewModel.setLists(placedAnchorNodes)
            viewModel.setRenderables(pointRender, widthLineRender, heightLineRender)
            // findNavController().navigate(com.google.ar.sceneform.rendering.R.id.action_arFragment_to_sceneViewFragment)
        }

        initObjects()

        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, motionEvent: MotionEvent ->
            if (cubeRenderable == null || distanceCardViewRenderable == null) return@setOnTapArPlaneListener

            refreshAim(
                hitResult,
                motionEvent,
                distanceCardViewRenderable!!
            )
        }

        binding.btnAdd.setOnClickListener {
            addFromAim()
        }

        arFragment.arSceneView.scene.addOnUpdateListener {

            touchScreenCenterConstantly()
            updateDistance()
        }
    }

    private fun updateDistance() {

        anchorNodeTemp?.let {

            if (::lineBetween.isInitialized) {
                arFragment.arSceneView.scene.removeChild(lineBetween)
            }

            if (currentAnchorNode.size < 2) {
                node1Pos = initialAnchor?.worldPosition
                node2Pos = anchorNodeTemp?.worldPosition
            } else {
                node1Pos = currentAnchorNode[currentAnchorNode.size - 1].worldPosition
                node2Pos = anchorNodeTemp?.worldPosition
            }
            calculateDistance(node1Pos!!, node2Pos!!)

        }
    }

    private fun calculateDistance(node1Pos: Vector3, node2Pos: Vector3): Float {
        difference = Vector3.subtract(node1Pos, node2Pos)
        totalLength += difference!!.length()
        val rotationFromAToB = Quaternion.lookRotation(
            difference!!.normalized(),
            Vector3.up()
        )
        //setting lines between points
        lineBetween = AnchorNode().apply {
            setParent(arFragment.arSceneView.scene)
            worldPosition = Vector3.add(node1Pos, node2Pos).scaled(.5f)
            worldRotation = rotationFromAToB
            localScale = Vector3(0.5f, 0.5f, difference!!.length())
            renderable = widthLineRender
        }
        //settinglabel
        if (lengthLabel == null) {
            lengthLabel = AnchorNode()
            lengthLabel!!.setParent(arFragment.arSceneView.scene)
        }
        lengthLabel!!.worldPosition = Vector3.add(node1Pos, node2Pos).scaled(.5f)
        initTextBoxes(difference!!.length(), lengthLabel!!, false)

        return difference!!.length()
    }

    private fun initObjects() {
        MaterialFactory.makeOpaqueWithColor(requireContext(), Color(Color.rgb(255, 255, 0)))
            .thenAccept { material: Material? ->
                heightLineRender = ShapeFactory.makeCube(
                    Vector3(.015f, 1f, 1f),
                    Vector3.zero(), material
                )
                heightLineRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        MaterialFactory.makeTransparentWithColor(requireContext(), Color(Color.rgb(255, 255, 0)))
            .thenAccept { material: Material? ->
                pointRender = ShapeFactory.makeCylinder(0.01f, 0.0003f, Vector3.zero(), material)
                pointRender!!.isShadowCaster = false
                pointRender!!.isShadowReceiver = false
            }

        ViewRenderable.builder()
            .setView(requireContext(), R.layout.distance)
            .build()
            .thenAccept { renderable: ViewRenderable ->
                renderable.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                    verticalAlignment = ViewRenderable.VerticalAlignment.BOTTOM
                }
                viewRenderable = renderable
            }

        Texture.builder()
            .setSource(
                requireContext(),
                R.drawable.aim
            )
            .build()
            .thenAccept { texture ->
                MaterialFactory.makeTransparentWithTexture(requireContext(), texture)
                    .thenAccept { material: Material? ->
                        aimRender = ShapeFactory.makeCylinder(0.01f, 0f, Vector3.zero(), material)
                        aimRender!!.isShadowCaster = false
                        aimRender!!.isShadowReceiver = false
                    }
            }

        MaterialFactory.makeTransparentWithColor(
            context,
            Color(Color.WHITE)
        ).thenAccept { material: Material? ->
            cubeRenderable = ShapeFactory.makeSphere(
                0.02f,
                Vector3.zero(),
                material
            )
            cubeRenderable!!.isShadowCaster = false
            cubeRenderable!!.isShadowCaster = false
            cubeRenderable!!.isShadowReceiver = false
        }

        MaterialFactory.makeOpaqueWithColor(requireContext(), Color(Color.rgb(255, 255, 0)))
            .thenAccept { material: Material? ->
                widthLineRender = ShapeFactory.makeCube(
                    Vector3(.01f, 0.01f, 1f),
                    Vector3.zero(), material
                )
                widthLineRender!!.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

        ViewRenderable
            .builder()
            .setView(context, R.layout.distance)
            .build()
            .thenAccept {
                distanceCardViewRenderable = it
                distanceCardViewRenderable!!.isShadowCaster = false
                distanceCardViewRenderable!!.isShadowReceiver = false
            }
            .exceptionally {
                val builder = AlertDialog.Builder(context)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }
    }

    private fun refreshAim(
        hitResult: HitResult,
        motionEvent: MotionEvent,
        renderable: ViewRenderable
    ) {
        if (motionEvent.metaState == 0) {
            if (anchorNodeTemp != null) {
                anchorNodeTemp!!.anchor!!.detach()
            }
            if (anchorNodeTemp == null) {
                initialAnchor = AnchorNode(hitResult.createAnchor())
                currentAnchorNode.add(initialAnchor!!)
                placedAnchorNodes.add(initialAnchor!!)
            }
//            if (placedAnchorNodes.size == 0) {
//                placeAnchor(hitResult, cubeRenderable!!)
//            } else if (placedAnchorNodes.size == 1) {
////            if (anchorNodeTemp != null) {
////                anchorNodeTemp!!.anchor!!.detach()
////            }
////            if (anchorNodeTemp == null) {
////                initialAnchor = AnchorNode(hitResult.createAnchor())
////                currentAnchorNode.add(initialAnchor!!)
////                tempAnchorNodes.add(initialAnchor!!)
////            }
//
//               // placeAnchor(hitResult, cubeRenderable!!)
//
//
//            } else {
//                clearAllAnchors()
//                placeAnchor(hitResult, cubeRenderable!!)
//            }
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)

            anchorNode.setParent(arFragment.arSceneView.scene)
            val transformableNode = TransformableNode(arFragment.transformationSystem)
            transformableNode.renderable = cubeRenderable
            transformableNode.setParent(anchorNode)
            arFragment.arSceneView.scene.addChild(anchorNode)
            anchorNodeTemp = anchorNode

        }
    }

    private fun clearAllAnchors() {
        placedAnchors.clear()
        for (anchorNode in placedAnchorNodes) {
            arFragment.arSceneView.scene.removeChild(anchorNode)
            anchorNode.isEnabled = false
            anchorNode.anchor!!.detach()
            anchorNode.setParent(null)
        }
        placedAnchorNodes.clear()
        midAnchors.clear()
        for ((k, anchorNode) in midAnchorNodes) {
            arFragment.arSceneView.scene.removeChild(anchorNode)
            anchorNode.isEnabled = false
            anchorNode.anchor!!.detach()
            anchorNode.setParent(null)
        }
        midAnchorNodes.clear()
        heightLineRender = null
    }

    // add points to the surface based on the crosshair position
    // add lines between points
    // add labels
    private fun addFromAim() {
        if (anchorNodeTemp != null) {
            placedAnchorNodes.add(anchorNodeTemp!!)
            val worldPosition = anchorNodeTemp!!.worldPosition
            val worldRotation = anchorNodeTemp!!.worldRotation
            // add point
            worldPosition.x += 0.001f
            val confirmedAnchorNode = AnchorNode()
            confirmedAnchorNode.worldPosition = worldPosition
            confirmedAnchorNode.worldRotation = worldRotation
            val anchor = confirmedAnchorNode.anchor
            confirmedAnchorNode.setParent(arFragment.arSceneView.scene)
            TransformableNode(arFragment.transformationSystem).apply {
                renderable = pointRender
                setParent(confirmedAnchorNode)
            }
            arFragment.arSceneView.scene.addChild(confirmedAnchorNode)
            currentAnchor.add(anchor)
            currentAnchorNode.add(confirmedAnchorNode)
            if (currentAnchorNode.size >= 2) {

                difference = Vector3.subtract(node1Pos, node2Pos)
                totalLength += difference!!.length()
                val rotationFromAToB =
                    Quaternion.lookRotation(difference!!.normalized(), Vector3.up())
                //setting lines between points
                AnchorNode().apply {
                    setParent(arFragment.arSceneView.scene)
                    this.worldPosition = Vector3.add(node1Pos, node2Pos).scaled(.5f)
                    this.worldRotation = rotationFromAToB
                    localScale = Vector3(1f, 1f, difference!!.length())
                    renderable = widthLineRender
                }
                //setting labels with distances
                labelArray.add(AnchorNode().apply {
                    setParent(arFragment.arSceneView.scene)
                    this.worldPosition = Vector3.add(node1Pos, node2Pos).scaled(.5f)
                    initTextBoxes(difference!!.length(), this, true)
                })
            }
        }
    }

    private fun initTextBoxes(
        meters: Float,
        transformableNode: AnchorNode,
        isFromCreateNewAnchor: Boolean
    ) {

        if (isFromCreateNewAnchor) {
            ViewRenderable.builder()
                .setView(requireContext(), R.layout.distance)
                .build()
                .thenAccept { renderable: ViewRenderable ->
                    renderable.apply {
                        isShadowCaster = false
                        isShadowReceiver = false
                        verticalAlignment = ViewRenderable.VerticalAlignment.BOTTOM
                    }

                    addDistanceCard(renderable, meters, transformableNode)


                }
        } else {
            addDistanceCard(viewRenderable, meters, transformableNode)
        }
    }

    private fun addDistanceCard(
        distanceRenderable: ViewRenderable,
        meters: Float,
        transformableNode: AnchorNode
    ) {
        distanceInMeters = distanceRenderable.view as CardView
        val metersString: String = if (meters < 1f) {
            String.format(Locale.ENGLISH, "%.0f", meters * 100) + " cm"
        } else {
            String.format(Locale.ENGLISH, "%.2f", meters) + " m"
        }
        val tv = distanceInMeters.getChildAt(0) as TextView
        tv.text = metersString
        Log.e("meters", metersString)
        transformableNode.renderable = distanceRenderable
    }

    // imitate clicks to the center of the screen (to the crosshair)
    private fun touchScreenCenterConstantly() {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis() + 1
        val x = this.resources.displayMetrics.widthPixels.toFloat() / 2
        val y = this.resources.displayMetrics.heightPixels.toFloat() / 2
        val motionEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )
        arFragment.arSceneView.dispatchTouchEvent(motionEvent)
   }

    // rotate labels according to camera movements
    private fun labelsRotation() {
        val cameraPosition = arFragment.arSceneView.scene.camera.worldPosition
        for (labelNode in labelArray) {
            val labelPosition = labelNode.worldPosition
            val direction = Vector3.subtract(cameraPosition, labelPosition)
            val lookRotation = Quaternion.lookRotation(direction, Vector3.up())
            labelNode.worldRotation = lookRotation
        }
    }

    fun clearAnchors() {
        for (i in placedAnchorNodes) {
            arFragment.arSceneView.scene.removeChild(i)
        }
        placedAnchorNodes.clear()
        placedAnchors.clear()
        midAnchors.clear()
        midAnchorNodes.clear()
        labelArray.clear()
        totalLength = 0f

    }

    override fun onStart() {
        super.onStart()
        if (::arFragment.isInitialized) {
            arFragment.onStart()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::arFragment.isInitialized) {
            arFragment.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::arFragment.isInitialized) {
            arFragment.onResume()
        }
    }

    private fun placeAnchor(
        hitResult: HitResult,
        renderable: Renderable
    ) {
        val anchor = hitResult.createAnchor()
        placedAnchors.add(anchor)

        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment.arSceneView.scene)
        }
        placedAnchorNodes.add(anchorNode)

        val node = TransformableNode(arFragment.transformationSystem)
            .apply {
                this.rotationController.isEnabled = false
                this.scaleController.isEnabled = false
                this.translationController.isEnabled = true
                this.renderable = renderable
                setParent(anchorNode)
            }

        arFragment.arSceneView.scene.addOnUpdateListener(this)
        arFragment.arSceneView.scene.addChild(anchorNode)
        node.select()
    }

    private fun measureDistanceFromGround() {
        if (fromGroundNodes.size == 0) return
        for (node in fromGroundNodes) {
            val textView = (distanceCardViewRenderable!!.view as LinearLayout)
                .findViewById<TextView>(R.id.planetInfoCard)
            val distanceCM = changeUnit(node[0].worldPosition.y + 1.0f, "cm")
            textView.text = "%.0f".format(distanceCM) + " cm"
        }
    }

    private fun changeUnit(distanceMeter: Float, unit: String): String {
        return when (unit) {
            "cm" -> (distanceMeter * 100).toString()
            "mm" -> (distanceMeter * 1000).toString()
            else -> distanceMeter.toString()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onUpdate(frame: FrameTime?) {
        //get the frame from the scene for shorthand
        val frame = arFragment.arSceneView.arFrame
        if (frame != null) {
            //get the trackables to ensure planes are detected
            val var3 = frame.getUpdatedTrackables(Plane::class.java).iterator()
            while (var3.hasNext()) {
                val plane = var3.next() as Plane

                if (plane.type == Plane.Type.VERTICAL) {
                    Toast.makeText(context, "Vertical plane", Toast.LENGTH_SHORT).show()
//                    if (Build.VERSION.SDK_INT >= 31) {
//                        val vibratorManager =
//                            requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
//                        val vibrator = vibratorManager.defaultVibrator
//                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
//                    } else {
//                        val v =
//                            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
//                        if (Build.VERSION.SDK_INT >= 26) {
//                            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
//                        } else {
//                            v.vibrate(200L)
//                        }
//
//                    }
                    measureDistanceOf2PointsVertical()
                } else if (plane.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING) {
                    Toast.makeText(context, "Ceiling, camera looking up at", Toast.LENGTH_SHORT)
                        .show()
//                    if (Build.VERSION.SDK_INT >= 31) {
//                        val vibratorManager =
//                            requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
//                        val vibrator = vibratorManager.defaultVibrator
//                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
//                    } else {
//                        val v =
//                            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
//                        if (Build.VERSION.SDK_INT >= 26) {
//                            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
//                        } else {
//                            v.vibrate(200L)
//                        }
//
//                    }
                } else if (plane.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING) {
//                    if (Build.VERSION.SDK_INT >= 31) {
//                        val vibratorManager =
//                            requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
//                        val vibrator = vibratorManager.defaultVibrator
//                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
//                    } else {
//                        val v =
//                            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
//                        if (Build.VERSION.SDK_INT >= 26) {
//                            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
//                        } else {
//                            v.vibrate(200L)
//                        }
//
//                    }
                    Toast.makeText(
                        context,
                        "Floor, ground, camera looking down at",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }
        }
        measureDistanceOf2Points()

    }

    private fun measureDistanceOf2Points() {
        if (placedAnchorNodes.size == 2) {
            val distanceMeter = calculateDistance(
                placedAnchorNodes[0].worldPosition,
                placedAnchorNodes[1].worldPosition
            )
            measureDistanceOf2Points(distanceMeter)
        }
    }

    private fun measureDistanceOf2PointsVertical() {

        if (placedAnchorNodes.size == 2) {
            val distanceMeter = calculateDistance(
                placedAnchorNodes[0].worldPosition,
                placedAnchorNodes[1].worldPosition
            )
            measureDistanceOf2Points(distanceMeter)
        }
    }

    private fun measureDistanceOf2Points(distanceMeter: Float) {
        val distanceTextCM = changeUnit(distanceMeter, "cm")
        val textView = (distanceCardViewRenderable!!.view as CardView)
            .findViewById<TextView>(R.id.planetInfoCard)
        textView.text = distanceTextCM
        Log.d(TAG, "distance: ${distanceTextCM}")
    }

    private fun tapDistanceFromGround(hitResult: HitResult){
        clearAllAnchors()
        val anchor = hitResult.createAnchor()
        placedAnchors.add(anchor)

        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
        placedAnchorNodes.add(anchorNode)

        val transformableNode = TransformableNode(arFragment!!.transformationSystem)
            .apply{
                this.rotationController.isEnabled = false
                this.scaleController.isEnabled = false
                this.translationController.isEnabled = true
                this.renderable = renderable
                setParent(anchorNode)
            }

        val node = Node()
            .apply {
                setParent(transformableNode)
                this.worldPosition = Vector3(
                    anchorNode.worldPosition.x,
                    anchorNode.worldPosition.y,
                    anchorNode.worldPosition.z)
                this.renderable = distanceCardViewRenderable
            }


        val arrow10UpNode = AnchorNode()
            .apply {
                setParent(node)
                this.worldPosition = Vector3(
                    node.worldPosition.x,
                    node.worldPosition.y+0.18f,
                    node.worldPosition.z
                )
                this.renderable = pointRender
                this.setOnTapListener { hitTestResult, motionEvent ->
                    node.worldPosition = Vector3(
                        node.worldPosition.x,
                        node.worldPosition.y+0.1f,
                        node.worldPosition.z
                    )
                }
            }


        //fromGroundNodes.add(listOf(node, arrow1UpNode, arrow1DownNode, arrow10UpNode, arrow10DownNode))

        arFragment.arSceneView.scene.addOnUpdateListener(this)
        arFragment.arSceneView.scene.addChild(anchorNode)
        transformableNode.select()
    }

}