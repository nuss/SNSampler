SNSamplerOSCPanel {
	classvar <all, <env;
	var <sampler, <>oscAddr, <>oscCmdPrefix, <>backupBuffersPrefix;
	var <>cmdNameTemplates;
	var widgetNameTemplates, inNames;

	*initClass {
		#all, env = ()!2;
	}

	*new { |sampler, oscAddr, oscCmdPrefix="/sampler", backupBuffersPrefix, cmdTemplates|
		if (sampler.isNil or: { sampler.class != SNSampler }) {
			Error("A new SNSamplerOSCPanel needs an existing SNSampler instance!").throw;
		} {
			if (all[sampler.name].notNil) {
				"A SNSamplerOSCPanel already for SNSampler '%' already exists".format(sampler.name).error;
				^nil;
			} {
				^super.newCopyArgs(sampler, oscAddr, oscCmdPrefix, backupBuffersPrefix).init(cmdTemplates);
			}
		}
	}

	init { |cmdTemplates|
		var wName;

		all.put(sampler.name, this);
		widgetNameTemplates = (
			ins: "%-inBus%",
			buffers: "%-activateBuffer%",
			resetBufs: "%-resetBuffer%",
			bufferStatuses: "%-bufferStaus%",
			resetAll: "%-resetAll",
			startStop: "%-start/Stop",
		);
		if (cmdTemplates.isNil) {
			this.cmdNameTemplates = (
				selectInBus: "%/in_select/%",
				displayInBus: "%/in%",
				selectBuffer: "%/select_buffer/%/1",
				zeroBuffer: "%/zero_buffer/%/1",
				bufferStatus: "%/buffer_status%",
				startStop: "%/start_stop",
				zeroAllBuffers: "%/zero_all"
			)
		} {
			this.cmdTemplates = cmdTemplates
		};

		env = (sampler: sampler -> (
			panel: this
		));

		sampler.controllerKeys = sampler.controllerKeys.add(\osc);

		sampler.numBuffers.do { |i|
			wName = widgetNameTemplates.ins.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, tab: sampler.name, svItems: sampler.inKeys ? [\nil]);
			this.oscAddr !? {
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectInBus.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'set in label', "{ |sv|
				Environment.push(SNSamplerOSCPanel.env);
				~sampler.value.panel.oscAddr !? {
					~sampler.value.panel.oscAddr.sendMsg(
						~sampler.value.panel.cmdNameTemplates.displayInBus.format(~sampler.value.panel.oscCmdPrefix, %), sv.item
					);
					~sampler.value.panel.oscAddr.sendMsg(
						~sampler.value.panel.cmdNameTemplates.selectInBus.format(~sampler.value.panel.oscCmdPrefix, %), sv.input
					);
				};
				Environment.pop
			}".format(i+1, i+1));
			wName = widgetNameTemplates.buffers.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, \false, tab: sampler.name);
			this.oscAddr !? {
				// weird hack: why do I have to disconnect first???
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'activate buffer for sampling', "{ |sv|
				Environment.push(SNSamplerOSCPanel.env);
				~sampler.key.prepareRecording(sv.value.asBoolean, %, ~sampler.key.ins[CVCenter.at('%').item]);
				~sampler.value.panel.oscAddr !? {
					~sampler.value.panel.oscAddr.sendMsg('%', sv.input)
				};
				Environment.pop
			}".format(
				i, widgetNameTemplates.ins.format(sampler.name, i+1),
				this.cmdNameTemplates.selectBuffer.format(this.oscCmdPrefix, i+1)
			));
			wName = widgetNameTemplates.resetBufs.format(sampler.name, i+1).asSymbol;
			CVCenter.use(wName, \false, tab: sampler.name);
			this.oscAddr !? {
				// weird hack: why do I have to disconnect first???
				CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.zeroBuffer.format(this.oscCmdPrefix, i+1));
			};
			CVCenter.addActionAt(wName, 'zero buffer', "{ |cv|
				Environment.push(SNSamplerOSCPanel.env);
				~sampler.key.reset(%);
				~sampler.value.panel.oscAddr !? {
					~sampler.value.panel.oscAddr.sendMsg('%', cv.input)
				};
				Environment.pop
			}".format(i, this.cmdNameTemplates.zeroBuffer.format(this.oscCmdPrefix, i+1)));
		};
		wName = widgetNameTemplates.resetAll.format(sampler.name).asSymbol;
		CVCenter.use(wName, \false, tab: sampler.name);
		this.oscAddr !? {
			CVCenter.cvWidgets[wName].oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.zeroAllBuffers.format(this.oscCmdPrefix));
		};
		CVCenter.addActionAt(wName, 'zero all buffers', { |cv|
			Environment.push(SNSamplerOSCPanel.env);
			~sampler.key.reset;
			~sampler.value.panel.oscAddr !? {
				~sampler.key.buffers.do { |buf, i|
					~sampler.value.panel.oscAddr.sendMsg(~sampler.value.panel.cmdNameTemplates.bufferStatus.format(~sampler.value.panel.oscCmdPrefix, i+1), 0)
				}
			};
			Environment.pop
		});
		wName = widgetNameTemplates.startStop.format(sampler.name).asSymbol;
		CVCenter.use(wName, \false, tab: sampler.name);
		CVCenter.cvWidgets[wName].setSoftWithin(0);
		this.oscAddr !? {
			CVCenter.cvWidgets[wName].oscDisconnect.oscConnect(this.oscAddr.ip, name: this.cmdNameTemplates.startStop.format(this.oscCmdPrefix))
		};
		CVCenter.addActionAt(wName, 'start/stop sampling', { |cv|
			Environment.push(SNSamplerOSCPanel.env);
			~sampler.key.sample(cv.input.asBoolean);
			~sampler.value.panel.oscAddr !? {
				~sampler.value.panel.oscAddr.sendMsg(~sampler.value.panel.cmdNameTemplates.startStop.format(~sampler.value.panel.oscCmdPrefix), cv.input)
			};
			Environment.pop
		});

		this.prInitController;
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
			sampler.ins.putAll(inPairs);
		}
	}

	prInitController {
		var isSampling = false;

		sampler.controllerKeys_(sampler.controllerKeys.add(\samplerOscPanel));

		sampler.mc.samplingController.put(\samplerOscPanel, { |changer, what|
			isSampling = changer.value;
			this.oscAddr !? {
				if (isSampling) {
					sampler.mc.recBufInsModel.value.keys.do { |n|
						// keys are Symbols!!
						this.oscAddr.sendMsg(
							this.cmdNameTemplates.bufferStatus.format(this.oscCmdPrefix, n.asInteger+1),
							isSampling.asInteger
						);
					}
				} {
					sampler.numBuffers.do { |i|
						CVCenter.at(widgetNameTemplates.buffers.format(sampler.name, i+1).asSymbol).input_(0);
					}
				}
			}
		});

		sampler.mc.statusController.put(\samplerOscPanel, { |changer, what|
			this.oscAddr !? {
				sampler.numBuffers.do { |i|
					this.oscAddr.sendMsg(
						this.cmdNameTemplates.bufferStatus.format(this.oscCmdPrefix, i+1),
						changer.value.includes(i.asSymbol).asInteger
					)
				}
			}
		});

		sampler.mc.insController.put(\samplerOscPanel, { |changer, what|
			var inSelect = sampler.numBuffers.collect { |i| CVCenter.at(widgetNameTemplates.ins.format(sampler.name, i+1).asSymbol) };
			inSelect.do { |sv| sv.items_(changer.value[0]) };
		})
	}
}