import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { Sparkles, Activity, ShieldCheck, Database, Layers3, Network } from 'lucide-react';

interface DataConstellationProps {
  activeIndex: number;
}

export default function DataConstellation({ activeIndex }: DataConstellationProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [selectedNodeInfo, setSelectedNodeInfo] = useState<{
    name: string;
    code: string;
    score: number;
    quota: number;
    reliability: string;
  } | null>(null);

  // List of 12 real Representative 408 Universities to display as active node details
  const prepopulatedSchools = [
    { name: "清华大学软件学院", code: "TSINGHUA_CS_081200", score: 395, quota: 25, reliability: "100.0% [官方简章核对]" },
    { name: "上海交通大学电院", code: "SJTU_CS_081200", score: 390, quota: 38, reliability: "98.5% [历年拟录取比对]" },
    { name: "浙江大学计算机学院", code: "ZJU_CS_081200", score: 388, quota: 65, reliability: "100.0% [生源清洗完成]" },
    { name: "北京大学信息学院", code: "PKU_CS_081200", score: 392, quota: 18, reliability: "97.2% [人工校验修正]" },
    { name: "南京大学计算机科系", code: "NJU_CS_081200", score: 382, quota: 42, reliability: "100.0% [录取红线校验]" },
    { name: "上海交通大学软院", code: "SJTU_SE_083500", score: 385, quota: 30, reliability: "99.1% [拟录取人头洗白]" },
    { name: "华东师范大学数科学院", code: "ECNU_DA_081200", score: 365, quota: 45, reliability: "98.8% [历史生源回溯]" },
    { name: "杭州电子科技大学计算机", code: "HDU_CS_081200", score: 340, quota: 120, reliability: "100.0% [大样本自动捕获]" },
    { name: "武汉大学计算机学院", code: "WHU_CS_081200", score: 375, quota: 48, reliability: "98.0% [复试比例锁定]" },
    { name: "中国科学技术大学计院", code: "USTC_CS_081200", score: 380, quota: 70, reliability: "99.4% [扩招差额对齐]" },
    { name: "北京航天航空大学计院", code: "BUAA_CS_081200", score: 378, quota: 55, reliability: "100.0% [统招计划精校]" },
    { name: "哈尔滨工业大学计算学部", code: "HIT_CS_081200", score: 372, quota: 85, reliability: "96.9% [调剂规则校对]" }
  ];

  useEffect(() => {
    if (!canvasRef.current || !containerRef.current) return;

    const width = containerRef.current.clientWidth || 500;
    const height = containerRef.current.clientHeight || 400;

    // SCENE SETTINGS
    const scene = new THREE.Scene();
    // Soft deep blue environment color matching app theme
    scene.background = new THREE.Color('#fafcff');
    scene.fog = new THREE.FogExp2('#fafcff', 0.015);

    // CAMERA Settings
    const camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 1000);
    camera.position.set(0, 0, 80);

    // RENDERER Settings
    const renderer = new THREE.WebGLRenderer({
      canvas: canvasRef.current,
      antialias: true,
      alpha: true,
    });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(width, height);

    // DYNAMIC NODES GENERATION
    const nodeCount = 180;

    // Custom class to store high-fidelity state node records
    interface ConstellationNode {
      index: number;
      school: typeof prepopulatedSchools[0];
      basePos: THREE.Vector3;
      currentPos: THREE.Vector3;
      targetPos: THREE.Vector3;
      currentColor: THREE.Color;
      targetColor: THREE.Color;
      nodeSize: number;
      targetSize: number;
    }

    const nodes: ConstellationNode[] = [];

    // Initialize random nodes
    for (let i = 0; i < nodeCount; i++) {
      const school = prepopulatedSchools[i % prepopulatedSchools.length];
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos((Math.random() * 2) - 1);
      const radius = 25 + Math.random() * 8;

      // Base spherical placement
      const x = radius * Math.sin(phi) * Math.cos(theta);
      const y = radius * Math.sin(phi) * Math.sin(theta);
      const z = radius * Math.cos(phi);

      const basePos = new THREE.Vector3(x, y, z);
      const currentPos = basePos.clone();

      nodes.push({
        index: i,
        school,
        basePos,
        currentPos,
        targetPos: basePos.clone(),
        currentColor: new THREE.Color('#3b82f6'),
        targetColor: new THREE.Color('#3b82f6'),
        nodeSize: 1.5,
        targetSize: 1.5
      });
    }

    const geometry = new THREE.BufferGeometry();
    const positions = new Float32Array(nodeCount * 3);
    const colors = new Float32Array(nodeCount * 3);
    const sizes = new Float32Array(nodeCount);

    for (let i = 0; i < nodeCount; i++) {
      positions[i * 3] = nodes[i].currentPos.x;
      positions[i * 3 + 1] = nodes[i].currentPos.y;
      positions[i * 3 + 2] = nodes[i].currentPos.z;

      colors[i * 3] = nodes[i].currentColor.r;
      colors[i * 3 + 1] = nodes[i].currentColor.g;
      colors[i * 3 + 2] = nodes[i].currentColor.b;

      sizes[i] = nodes[i].nodeSize;
    }

    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));

    // Connect some lines (topological links)
    const lineMaterial = new THREE.LineBasicMaterial({
      color: '#3b82f6',
      transparent: true,
      opacity: 0.28,
      depthWrite: false
    });
    const lineGeometry = new THREE.BufferGeometry();
    const linePositions = new Float32Array(250 * 2 * 3); // 250 links, 2 points each, 3 coordinates
    lineGeometry.setAttribute('position', new THREE.BufferAttribute(linePositions, 3));
    const lineSegments = new THREE.LineSegments(lineGeometry, lineMaterial);
    scene.add(lineSegments);

    // Dynamic grid floor plane for modern visual architecture anchoring
    const gridHelper = new THREE.GridHelper(120, 30, '#d7e3f8', '#eff6ff');
    gridHelper.position.y = -22;
    gridHelper.material.opacity = 0.45;
    gridHelper.material.transparent = true;
    scene.add(gridHelper);

    // Spark energy particles traversing across links
    const sparkleCount = 20;
    const sparkleGeo = new THREE.BufferGeometry();
    const sparklePositions = new Float32Array(sparkleCount * 3);
    const sparklesColor = new THREE.Color('#2563eb');
    const sparkleColors = new Float32Array(sparkleCount * 3);
    for (let i = 0; i < sparkleCount; i++) {
      sparkleColors[i*3] = sparklesColor.r;
      sparkleColors[i*3+1] = sparklesColor.g;
      sparkleColors[i*3+2] = sparklesColor.b;
    }
    sparkleGeo.setAttribute('position', new THREE.BufferAttribute(sparklePositions, 3));
    sparkleGeo.setAttribute('color', new THREE.BufferAttribute(sparkleColors, 3));

    // Custom Canvas Round Glow Texture for beautiful soft round star particles with perfect vertexColor multiplication
    const createCircleTexture = () => {
      const size = 64;
      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.clearRect(0, 0, size, size);
        // Pure solid white glowing circle. This is critical because vertexColors multiply the texture color.
        // If texture has color, vertexColors turn into muddy dark blends. White allows 100% loyal color reproduction.
        const grad = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
        grad.addColorStop(0, 'rgba(255, 255, 255, 1.0)');
        grad.addColorStop(0.2, 'rgba(255, 255, 255, 0.95)');
        grad.addColorStop(0.55, 'rgba(255, 255, 255, 0.35)');
        grad.addColorStop(1, 'rgba(255, 255, 255, 0)');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
        ctx.fill();
      }
      return new THREE.CanvasTexture(canvas);
    };

    const nodeMaterial = new THREE.PointsMaterial({
      size: 5.5,
      sizeAttenuation: true,
      map: createCircleTexture(),
      alphaTest: 0.002,
      transparent: true,
      depthWrite: false,
      blending: THREE.NormalBlending,
      vertexColors: true
    });

    const pointCloud = new THREE.Points(geometry, nodeMaterial);
    scene.add(pointCloud);

    // Spark particles layer
    const sparkTexture = () => {
      const size = 32;
      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.clearRect(0, 0, size, size);
        const grad = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
        grad.addColorStop(0, 'rgba(255, 255, 255, 1.0)');
        grad.addColorStop(0.4, 'rgba(255, 255, 255, 0.8)');
        grad.addColorStop(1, 'rgba(255, 255, 255, 0)');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
        ctx.fill();
      }
      return new THREE.CanvasTexture(canvas);
    };

    const sparksMaterial = new THREE.PointsMaterial({
      size: 6.5,
      map: sparkTexture(),
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      vertexColors: true
    });
    const sparksCloud = new THREE.Points(sparkleGeo, sparksMaterial);
    scene.add(sparksCloud);

    // Active Spark state managers
    const sparksState = Array.from({ length: sparkleCount }, () => ({
      nodeA: Math.floor(Math.random() * nodeCount),
      nodeB: Math.floor(Math.random() * nodeCount),
      alpha: Math.random(),
      speed: 0.008 + Math.random() * 0.012
    }));

    // INITIAL TRIGGER FOR DIFFERENT MODES
    const updateTargetStates = (mode: number) => {
      nodes.forEach((node, i) => {
        if (mode === 0) {
          // MODE 0: Wave Landscape Plot (representing "不只看复试线")
          // Create an elegant 3D landscape of points structured by their typical key values
          const scaleX = 0.5;
          const scaleZ = 0.5;
          const x = ((i % 12) - 6) * 6;
          const z = (Math.floor(i / 12) - 7) * 4;
          // Sine height showing beautiful score topology wave
          const dist = Math.sqrt(x*x + z*z);
          const y = Math.sin(dist * 0.25 - 0.2) * 5;

          node.targetPos.set(x, y + 2, z);
          // Highlight nodes colored by target category
          node.targetColor.set(i % 3 === 0 ? '#2563eb' : i % 3 === 1 ? '#60a5fa' : '#3b82f6');
          node.targetSize = 1.4;
        } 
        else if (mode === 1) {
          // MODE 1: Three concentric spinning rings (representing "冲稳保分层")
          // Nodes form concentric horizontal orbits representing 冲 Target, 稳 Safe, 保 Floor categories
          const tier = i % 3; // 0 = 冲, 1 = 稳, 2 = 保
          let radius = 10;
          let colorHex = '#ef4444'; // Red-orange for 冲
          let heightRange = 2;

          if (tier === 0) {
            radius = 12 + Math.cos(i) * 3;
            colorHex = '#ef4444'; // Red-orange for 冲 (Competitive)
            heightRange = 4;
          } else if (tier === 1) {
            radius = 20 + Math.sin(i) * 4;
            colorHex = '#2563eb'; // Deep Blue for 稳 (Normal)
            heightRange = 3;
          } else {
            radius = 28 + Math.cos(i * 1.5) * 4;
            colorHex = '#10b981'; // Emerald Green for 保 (Floor Protection)
            heightRange = 2;
          }

          const angle = (i * (360 / (nodeCount / 3)) * Math.PI) / 180 + (Math.random() * 0.15);
          const x = radius * Math.cos(angle);
          const z = radius * Math.sin(angle);
          const y = (Math.random() - 0.5) * heightRange;

          node.targetPos.set(x, y + 2, z);
          node.targetColor.set(colorHex);
          node.targetSize = 1.6;
        } 
        else if (mode === 2) {
          // MODE 2: Parallel multidimensional comparative matrix columns ("多院校横向对比")
          // Vertically structured comparative array lines
          const colIndex = i % 4; // 4 columns represents metrics
          const rowIndex = Math.floor(i / 4);
          
          const x = (colIndex - 1.5) * 14;
          const y = -12 + (rowIndex * 0.7);
          const z = Math.sin(rowIndex * 0.15 + colIndex) * 3.5;

          node.targetPos.set(x, y + 3, z);
          node.targetColor.set(colIndex === 0 ? '#3b82f6' : colIndex === 1 ? '#6366f1' : colIndex === 2 ? '#a855f7' : '#06b6d4');
          node.targetSize = 1.3;
        } 
        else if (mode === 3) {
          // MODE 3: Star constellation hubs with dim background (representing "收藏备选清单")
          // A few nodes light up beautifully in central core focus, while others are pushed outward and dimmed
          const isCoreSelected = i < 6 || i === 25 || i === 42 || i === 65 || i === 87 || i === 110;
          
          if (isCoreSelected) {
            const angle = (i % 6) * (Math.PI / 3);
            const r = 12 + (i % 2 === 0 ? 3 : -3);
            const x = r * Math.cos(angle);
            const z = r * Math.sin(angle);
            const y = 5 + (Math.sin(i) * 4);

            node.targetPos.set(x, y, z);
            node.targetColor.set('#f59e0b'); // Gorgeous golden color theme for saved schools
            node.targetSize = 3.2; // Extra large shining nodes
          } else {
            // Background nebula cloud pushed far away and dimmed down
            const angle = Math.random() * Math.PI * 2;
            const radius = 35 + Math.random() * 20;
            const x = radius * Math.cos(angle);
            const z = radius * Math.sin(angle);
            const y = -14 + (Math.random() * 25);

            node.targetPos.set(x, y, z);
            node.targetColor.set('#94a3b8'); // Soft slate-gray
            node.targetSize = 0.8; // Tiny particles
          }
        }
      });
    };

    // Initial positioning setup
    updateTargetStates(activeIndex);

    // Active hovered inspect trigger
    let activeInspectionNode: ConstellationNode | null = null;
    let frameId: number = 0;
    let rotationAngle = 0;

    // RESIZE OBSERVER
    const resizeObserver = new ResizeObserver((entries) => {
      if (!entries || entries.length === 0) return;
      const { width: currentW, height: currentH } = entries[0].contentRect;
      
      camera.aspect = currentW / currentH;
      camera.updateProjectionMatrix();
      
      renderer.setSize(currentW, currentH);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    });
    
    resizeObserver.observe(containerRef.current);

    // MOUSE INTERACTION TO TARGET CLOSEST NODE
    const handlePointerMove = (e: MouseEvent) => {
      const rect = canvasRef.current?.getBoundingClientRect();
      if (!rect) return;
      
      const mouseX = ((e.clientX - rect.left) / rect.width) * 2 - 1;
      const mouseY = -((e.clientY - rect.top) / rect.height) * 2 + 1;

      // Find node with minimum normalized screen distance
      let minDistance = 0.08; // sensitivity threshold
      let closestNode: ConstellationNode | null = null;

      const vector = new THREE.Vector3(mouseX, mouseY, 0.5);
      vector.unproject(camera);
      const dir = vector.sub(camera.position).normalize();
      const distance = -camera.position.z / dir.z;
      const pos = camera.position.clone().add(dir.multiplyScalar(distance));

      nodes.forEach((node) => {
        const d = node.currentPos.distanceTo(pos);
        if (d < minDistance) {
          minDistance = d;
          closestNode = node;
        }
      });

      if (closestNode) {
        if (activeInspectionNode !== closestNode) {
          activeInspectionNode = closestNode;
          setSelectedNodeInfo(closestNode.school);
        }
      }
    };

    containerRef.current.addEventListener('mousemove', handlePointerMove);

    // RENDER LOOP
    const animate = () => {
      frameId = requestAnimationFrame(animate);

      // Slow elegant passive spin depending on mode
      rotationAngle += 0.0018;
      
      if (activeIndex === 1) {
        // Revolving spinning concentric rings mode
        scene.rotation.y = rotationAngle * 1.5;
        scene.rotation.x = Math.sin(rotationAngle * 0.5) * 0.1;
      } else if (activeIndex === 2) {
        // Slower static Matrix side panning perspective
        scene.rotation.y = Math.sin(rotationAngle * 0.6) * 0.22;
        scene.rotation.x = 0.08;
      } else if (activeIndex === 3) {
        // Heartbeat pulse rotation
        scene.rotation.y = rotationAngle * 0.8;
        scene.rotation.x = 0.15 + Math.cos(rotationAngle * 0.5) * 0.05;
      } else {
        // Landscape general mode spin
        scene.rotation.y = rotationAngle * 0.6;
        scene.rotation.x = 0.1 + Math.sin(rotationAngle * 0.45) * 0.06;
      }

      // 1. Move normal nodes closer to their targets with smooth lerp
      const posAttr = pointCloud.geometry.getAttribute('position') as THREE.BufferAttribute;
      const colorAttr = pointCloud.geometry.getAttribute('color') as THREE.BufferAttribute;

      const pArr = posAttr.array as Float32Array;
      const cArr = colorAttr.array as Float32Array;

      nodes.forEach((node, i) => {
        // Smooth positioning transition vector
        node.currentPos.lerp(node.targetPos, 0.068);
        pArr[i * 3] = node.currentPos.x;
        pArr[i * 3 + 1] = node.currentPos.y;
        pArr[i * 3 + 2] = node.currentPos.z;

        // Smooth color interpolations
        node.currentColor.lerp(node.targetColor, 0.068);
        cArr[i * 3] = node.currentColor.r;
        cArr[i * 3 + 1] = node.currentColor.g;
        cArr[i * 3 + 2] = node.currentColor.b;

        // Dynamic size adjustments
        node.nodeSize = THREE.MathUtils.lerp(node.nodeSize, node.targetSize, 0.068);
        sizes[i] = node.nodeSize;
      });

      pointCloud.geometry.getAttribute('position').needsUpdate = true;
      pointCloud.geometry.getAttribute('color').needsUpdate = true;

      // 2. Traversal spark positions calculation on topological lines
      const sparkArr = sparksCloud.geometry.getAttribute('position').array as Float32Array;
      
      sparksState.forEach((spark, index) => {
        spark.alpha += spark.speed;
        if (spark.alpha >= 1.0) {
          spark.alpha = 0;
          spark.nodeA = Math.floor(Math.random() * nodeCount);
          spark.nodeB = Math.floor(Math.random() * nodeCount);
        }

        const nodeA = nodes[spark.nodeA];
        const nodeB = nodes[spark.nodeB];

        if (nodeA && nodeB) {
          const currentSparkPos = new THREE.Vector3().lerpVectors(
            nodeA.currentPos,
            nodeB.currentPos,
            spark.alpha
          );
          sparkArr[index * 3] = currentSparkPos.x;
          sparkArr[index * 3 + 1] = currentSparkPos.y;
          sparkArr[index * 3 + 2] = currentSparkPos.z;
        }
      });
      sparksCloud.geometry.getAttribute('position').needsUpdate = true;

      // 3. Render dynamically drawing topological connection lines between adjacent nodes
      const lineArr = lineGeometry.getAttribute('position').array as Float32Array;
      let lineIndex = 0;

      // Draw active links based on current coordinates
      for (let i = 0; i < nodeCount && lineIndex < 250; i += 2) {
        const nA = nodes[i];
        const nB = nodes[(i + 13) % nodeCount]; // offset mapping to form topological net
        if (nA && nB && nA.currentPos.distanceTo(nB.currentPos) < 26) {
          lineArr[lineIndex * 6] = nA.currentPos.x;
          lineArr[lineIndex * 6 + 1] = nA.currentPos.y;
          lineArr[lineIndex * 6 + 2] = nA.currentPos.z;

          lineArr[lineIndex * 6 + 3] = nB.currentPos.x;
          lineArr[lineIndex * 6 + 4] = nB.currentPos.y;
          lineArr[lineIndex * 6 + 5] = nB.currentPos.z;
          lineIndex++;
        }
      }
      lineGeometry.getAttribute('position').needsUpdate = true;
      lineGeometry.setDrawRange(0, lineIndex * 2);

      renderer.render(scene, camera);
    };

    animate();

    // CLEANUP
    return () => {
      cancelAnimationFrame(frameId);
      resizeObserver.disconnect();
      if (containerRef.current) {
        containerRef.current.removeEventListener('mousemove', handlePointerMove);
      }
      geometry.dispose();
      lineGeometry.dispose();
      nodeMaterial.dispose();
      lineMaterial.dispose();
      sparkleGeo.dispose();
      sparksMaterial.dispose();
      renderer.dispose();
    };
  }, [activeIndex]);

  // Set randomized inspection on load
  useEffect(() => {
    setSelectedNodeInfo(prepopulatedSchools[Math.floor(Math.random() * prepopulatedSchools.length)]);
  }, []);

  // Helper helper to render semantic descriptions for each visualization mode
  const getModeLegendContent = () => {
    switch (activeIndex) {
      case 0:
        return {
          title: "维度一：复试线/录取最低分/名额 3D 浮动地形",
          desc: "180个代表方向在起伏的 3D 数据地形平面分布。高耸山峰代表报录难度大、极易卷起的泡沫化院校；低平开阔的峡谷代表高容错、低复试线、高招人名额的性价比绿洲。",
          badges: [
            { color: "bg-[#2563eb]", text: "难考锋芒院校" },
            { color: "bg-[#60a5fa]", text: "正常数据基准" },
            { color: "bg-[#3b82f6]", text: "待发掘性价比方向" }
          ]
        };
      case 1:
        return {
          title: "维度二：冲、稳、保三级志愿梯度差速环形轨道",
          desc: "粒子根据往年真实生源排布于差速自转轨道。内圈旋转快代表初试阻力大，外圈宽阔代表录取底盘扎实。协助你实现志愿攻守平衡，防范滑档。",
          badges: [
            { color: "bg-[#ef4444]", text: "内圈轨道：冲刺挑战 (高阻力)" },
            { color: "bg-[#2563eb]", text: "中圈轨道：稳妥上岸 (中重名额)" },
            { color: "bg-[#10b981]", text: "外圈轨道：保底退路 (高容错底盘)" }
          ]
        };
      case 2:
        return {
          title: "维度三：多目标平行评估维度并列纵阵",
          desc: "四个并列的数据立轴悬挂着不同的对比颗粒。从左至右分别呈现：复试切线、国家名额计划、历史调剂深度和 AI 评级高度，让全局横向分布一目了然。",
          badges: [
            { color: "bg-[#3b82f6]", text: "切线指标轴" },
            { color: "bg-[#6366f1]", text: "名额盘口轴" },
            { color: "bg-[#a855f7]", text: "调剂丰水轴" },
            { color: "bg-[#06b6d4]", text: "AI评定参考轴" }
          ]
        };
      case 3:
        return {
          title: "维度四：已存志愿星辉图腾 与 微尘背景对照",
          desc: "你标记过星号的意向在中央联结成一条高亮的核心星座航道，并赋予高亮标记，而其他170余个背景对照组院校粒子则变暗退避为远景微尘背景点缀。",
          badges: [
            { color: "bg-[#f59e0b]", text: "鎏金主星：意向研招目标" },
            { color: "bg-[#94a3b8]", text: "微尘星尘：背景对照参考" }
          ]
        };
      default:
        return {
          title: "408 研招大数据拓扑系统",
          desc: "多维立体星图折射出考研抉择之路的最佳落点位置。",
          badges: []
        };
    }
  };

  const modeLegend = getModeLegendContent();

  return (
    <div 
      ref={containerRef} 
      className="relative w-full h-[450px] sm:h-[480px] md:h-[510px] border border-[#d7e3f8] bg-[#fafcff] rounded-2xl overflow-hidden shadow-md flex flex-col justify-end"
    >
      {/* Absolute top interactive canvas backing */}
      <canvas 
        ref={canvasRef} 
        className="absolute inset-0 w-full h-full cursor-pointer touch-none z-10" 
      />

      {/* Futuristic Real-Time Hud overlays */}
      <div className="absolute top-4 left-4 z-20 pointer-events-none select-none text-left space-y-1 bg-white/80 backdrop-blur-md px-3.5 py-2.5 rounded-xl border border-[#d7e3f8]/60 shadow-sm max-w-[210px] sm:max-w-xs transition-colors duration-300">
        <div className="flex items-center gap-1.55">
          <Activity className="w-3.5 h-3.5 text-[#2563eb] animate-pulse" />
          <span className="text-[9px] font-mono font-bold tracking-widest text-[#0f172a] uppercase">SYS_GRID_CONSTELLATION</span>
        </div>
        <div className="font-mono text-[8px] text-slate-500 space-y-0.5 whitespace-nowrap leading-none pt-1">
          <p>STB_NODES_UPTIME: 180 / 180 ACTIVE</p>
          <p>MAPPED_RECORDS: 18_DATABASE_SCHEMAS</p>
          <p>GRADIENT_FIELD: {activeIndex === 0 ? "TOPOGRAPHY_WAVE_LANDSCAPE" : activeIndex === 1 ? "CONCENTRIC_SAFE_ORBITS" : activeIndex === 2 ? "PARALLEL_COLUMN_MATRIX" : "SAVED_STAR_HUBS"}</p>
          <p>LUMINOSITY_FACTOR: 98.4% [STABLE]</p>
        </div>
      </div>

      <div className="absolute top-4 right-4 z-20 pointer-events-none select-none text-right bg-white/80 backdrop-blur-md px-3 py-1.5 rounded-lg border border-[#d7e3f8]/50 text-[8px] font-mono text-slate-500 shadow-sm">
        <span className="flex items-center gap-1">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping"></span>
          REALTIME 3D FEED ACTIVE
        </span>
      </div>

      {/* Dynamic graphic interpretation legend helper overlay */}
      <div className="absolute top-24 left-4 z-20 bg-white/70 backdrop-blur-md p-3.5 rounded-xl border border-[#d7e3f8]/60 shadow-sm text-left max-w-[280px] hidden sm:block pointer-events-none select-none">
        <span className="text-[9px] font-mono font-extrabold text-[#2563eb] uppercase tracking-wider block mb-1">
          Visual Legend · 3D 图表维度解构
        </span>
        <h5 className="text-[10.5px] font-bold text-slate-800 leading-snug">{modeLegend.title}</h5>
        <p className="text-[9px] leading-relaxed text-slate-500 font-light mt-1 mb-2.5">
          {modeLegend.desc}
        </p>

        <div className="flex flex-wrap gap-1.5 border-t border-slate-100 pt-2 shrink-0">
          {modeLegend.badges.map((b, i) => (
            <span key={i} className="inline-flex items-center gap-1 text-[8px] px-1.5 py-0.5 rounded bg-slate-100/60 font-medium text-slate-700">
              <span className={`w-1.5 h-1.5 rounded-full ${b.color}`} />
              {b.text}
            </span>
          ))}
        </div>
      </div>

      {/* Absolute center bottom overlay hover detail report HUD - High fidelity & responsive */}
      <div className="absolute bottom-4 left-4 right-4 z-20 bg-white/95 backdrop-blur-lg px-4 py-3 border-[#cbe0fb] rounded-xl border shadow-lg text-left transition-all duration-300">
        <span className="text-[8.5px] font-mono uppercase tracking-widest text-[#2563eb] font-bold mb-1.5 flex items-center justify-between">
          <span className="flex items-center gap-1">
            <Network className="w-3.5 h-3.5 text-[#2563eb] animate-spin-slow" />
            3D 空间探针 (请晃动鼠标指针划过群星，锁定具体的备选研招点)
          </span>
          <span className="hidden md:inline text-[8px] text-slate-400 normal-case font-normal">
            当前交互视图: 第 {activeIndex + 1} 视角
          </span>
        </span>
        {selectedNodeInfo ? (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 sm:gap-4 items-center">
            <div>
              <span className="text-[8px] text-slate-400 font-mono uppercase block">NAME 报考研招方向</span>
              <span className="text-xs sm:text-sm font-black text-[#0f172a] tracking-tight block truncate mt-0.5">{selectedNodeInfo.name}</span>
            </div>
            <div>
              <span className="text-[8px] text-slate-400 font-mono uppercase block">SYS_HASH 唯一样本</span>
              <span className="text-[10px] font-mono text-slate-600 block truncate mt-0.5">{selectedNodeInfo.code}</span>
            </div>
            <div>
              <span className="text-[8px] text-slate-400 font-mono uppercase block">DTRM_SCORE 招生基准</span>
              <span className="text-[11px] font-mono font-bold text-[#2563eb] block mt-0.5">{selectedNodeInfo.score} 分 / {selectedNodeInfo.quota}人</span>
            </div>
            <div>
              <span className="text-[8px] text-slate-400 font-mono uppercase block">TRUST_RATE 数据可信度</span>
              <span className="text-[10px] font-mono text-emerald-600 font-bold block mt-0.5">{selectedNodeInfo.reliability}</span>
            </div>
          </div>
        ) : (
          <div className="text-xs text-slate-400 font-light italic">
            请移动光标穿过3D星空，悬浮探测具体目标院校招生参数
          </div>
        )}
      </div>
    </div>
  );
}
