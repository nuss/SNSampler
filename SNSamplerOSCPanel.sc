SNSamplerOSCPanel {
	classvar <all;
	var <sampler, <>oscAddr, <>oscCmdPrefix, <>backupBuffersPrefix, <ins;
	var <>cmdNameTemplates;
	var widgetNameTemplates, inNames;

	*initClass {
		all = ();
	}

	*new { |sampler, oscAddr, oscCmdPrefix="/sampler", backupBuffersPrefix, ins|
		if (sampler.isNil or: { sampler.class != SNSampler }) {
			Error("A new SNSamplerOSCPanel needs an existing SNSampler instance!").throw;
		} {
			if (all[sampler.name].notNil) {
				"A SNSamplerOSCPanel already for SNSampler '%' already exists".format(sampler.name).error;
				^nil;
			} {
				^super.newCopyArgs(sampler, oscAddr, oscCmdPrefix, backupBuffersPrefix, ins).init;
			}
		}
	}

	init {
		var inBusses, inKeys, insSpec = \audioin.asSpec, wName;

		all.put(sampler.name, this);
		widgetNameTemplates = (
			ins: "%-inBus%",
			buffers: "%-activateBuffer%",
			resetBufs: "%-resetBuffer%",
			resetAll: "%-resetAll",
			startStop: "%-start/Stop",
		);
		this.cmdNameTemplates = (
			selectInBus: "%/in_select/%",
			displayInBus: "%/in%",
			selectBuffer: "%/select_buffer/%/1",
			// selectBuffer: "%/select_buffer%",
			zeroBuffer: "%/zero_buffer/%/1",
			bufferStatus: "%/buffer_status%",
			startStop: "%/start_stop",
			zeroAllBuffers: "%/zero_all"
		);
		ins ?? {
			inBusses = (insSpec.minval..insSpec.maxval);
			inKeys = inBusses.collect(_.asSymbol);
			ins = inBusses.collect { |bus| bus.asSymbol -> bus }.asEvent;
		};
		sampler.controllerKeys = sampler.controllerKeys.add(\osc);

		sampler.numBuffers.do { |i|
			wName = widgetNameTemplates.ins.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, tab: sampler.name, svItems: inKeys ? [\nil]);
			this.oscAddr !? {
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectInBus.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'set in label', "{ |sv|
				var oscPanel = SNSamplerOSCPanel.all['%'];
				oscPanel.oscAddr !? {
					oscPanel.oscAddr.sendMsg(
						oscPanel.cmdNameTemplates.displayInBus.format(oscPanel.oscCmdPrefix, %), sv.item
					);
					oscPanel.oscAddr.sendMsg(
						oscPanel.cmdNameTemplates.selectInBus.format(oscPanel.oscCmdPrefix, %), sv.input
					);
				}
			}".format(sampler.name, i+1, i+1));
			wName = widgetNameTemplates.buffers.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, \false, tab: sampler.name);
			this.oscAddr !? {
				// weird hack: why do I have to disconnect first???
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'activate buffer for sampling', "{ |sv|
				var sampler = SNSampler.all['%'];
				var oscPanel = SNSamplerOSCPanel.all['%'];
				sampler.prepareRecording(sv.value.asBoolean, %, oscPanel.ins[CVCenter.at('%').item]);
				oscPanel.oscAddr !? {
					oscPanel.oscAddr.sendMsg(\"%\", sv.input)
				}
			}".format(
				sampler.name, sampler.name, i,
				widgetNameTemplates.ins.format(sampler.name, i+1),
				this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1)
			));
			wName = widgetNameTemplates.resetBufs.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, \false, tab: sampler.name);
			this.oscAddr !? {
				// weird hack: why do I have to disconnect first???
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.zeroBuffer.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'zero buffer', "{ |cv|
				var sampler = SNSampler.all['%'];
				var oscPanel = SNSamplerOSCPanel.all['%'];
				sampler.reset(%);
				oscPanel.oscAddr !? {
					oscPanel.oscAddr.sendMsg(\"%\", cv.input)
				}
			}".format(
				sampler.name, sampler.name, i,
				this.cmdNameTemplates.zeroBuffer.format(this.oscCmdPrefix, i+1)
			));
		};
		CVCenter.use(widgetNameTemplates.resetAll.format(sampler.name), \false, tab: sampler.name);
		CVCenter.use(widgetNameTemplates.startStop.format(sampler.name), \false, tab: sampler.name);
	}

	addIns { |inPairs|
		if (inPairs.size < 2) {
			Error("inPairs must at least consist of one key and one value").throw
		} {
			inPairs = inPairs.asEvent;
			if (inPairs.keys.select { |k| k.class == Symbol }.size < inPairs.keys.size) {
				Error("Keys given inPairs must be symbols!").throw
			};
			if (inPairs.values.select { |v| v.class == Integer }.size < inPairs.values.size) {
				Error("Input channels given in inPairs must be integers!").throw
			};
			sampler.numBuffers.do { |i|
				CVCenter.at(widgetNameTemplates.ins.format(sampler.name, i+1)).items_(
					CVCenter.at(widgetNameTemplates.ins.format(sampler.name, i+1)).items ++ inPairs.keys
				)
			};
			ins.putAll(inPairs);
		}
	}
}